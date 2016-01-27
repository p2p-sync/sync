package org.rmatil.sync.core.syncer.file;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.ConflictHandler;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.core.api.IFileSyncer;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Initializes the file offering protocol for each local file event which
 * has been passed to the syncer
 */
public class FileSyncer implements IFileSyncer {

    protected static final Logger logger = LoggerFactory.getLogger(FileSyncer.class);

    public static final int NUMBER_OF_SYNCS = 1;

    protected       IUser           user;
    protected       IClient         client;
    protected       IClientManager  clientManager;
    protected       IStorageAdapter storageAdapter;
    protected       IObjectStore    objectStore;
    protected final List<IEvent>    eventsToIgnore;

    protected MBassador<IBusEvent> globalEventBus;
    protected ExecutorService      syncExecutor;

    protected ClientDevice clientDevice;

    public FileSyncer(IUser user, IClient client, IClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus) {
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;

        this.clientDevice = new ClientDevice(user.getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress());

        syncExecutor = Executors.newFixedThreadPool(NUMBER_OF_SYNCS);

        this.eventsToIgnore = Collections.synchronizedList(new ArrayList<>());
    }

    @Handler
    public void handleBusEvent(IgnoreBusEvent event) {
        // ignore the given event if it arises in sync()
        logger.debug("Got ignore event from global event bus: " + event.getEvent().getEventName() + " for file " + event.getEvent().getPath().toString());
        synchronized (this.eventsToIgnore) {
            this.eventsToIgnore.add(event.getEvent());
        }
    }

    @Override
    public void sync(IEvent event)
            throws SyncFailedException {

        long start = System.currentTimeMillis();
        synchronized (this.eventsToIgnore) {
            for (IEvent eventToCheck : this.eventsToIgnore) {
                // weak ignoring events
                if (eventToCheck.getEventName().equals(event.getEventName()) &&
                        eventToCheck.getPath().toString().equals(event.getPath().toString())) {

                    logger.info("Ignoring syncing of event " + event.getEventName() + " for path " + event.getPath().toString());
                    this.eventsToIgnore.remove(event);
                    return;
                }
            }
        }

        // TODO: check why we sometimes still get modify events for directories despite adding the IgnoreDirModifier
        if (event instanceof ModifyEvent) {
            try {
                if (this.storageAdapter.isDir(new LocalPathElement(event.getPath().toString()))) {
                    logger.info("Skipping unintentionally received modified event for directory " + event.getPath().toString());
                    return;
                }
            } catch (InputOutputException e) {
                logger.error("Failed to check whether the modify event for " + event.getPath().toString() + " should be ignored. Therefore will sync...");
            }
        }

        logger.debug("Syncing event " + event.getEventName() + " for path " + event.getPath().toString());

        UUID fileExchangeId = UUID.randomUUID();

        // all events require an offering step

        FileOfferExchangeHandler fileOfferExchangeHandler = new FileOfferExchangeHandler(
                fileExchangeId,
                this.clientDevice,
                this.clientManager,
                this.client,
                this.objectStore,
                this.storageAdapter,
                this.globalEventBus,
                event
        );

        logger.debug("Starting file offer exchange handler for exchangeId " + fileExchangeId);

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, fileOfferExchangeHandler);
        Thread fileOfferExchangeHandlerThread = new Thread(fileOfferExchangeHandler);
        fileOfferExchangeHandlerThread.setName("FileOfferExchangeHandler-" + fileExchangeId);
        fileOfferExchangeHandlerThread.start();

        logger.debug("Waiting for offer exchange " + fileExchangeId + " to complete... (Max. " + FileOfferExchangeHandler.MAX_WAITING_TIME + "ms)");
        try {
            fileOfferExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Failed to await for file offer exchange " + fileExchangeId + ". Message: " + e.getMessage());
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(fileExchangeId);

        if (! fileOfferExchangeHandler.isCompleted()) {
            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file offering");
            return;
        }

        FileOfferExchangeHandlerResult result = fileOfferExchangeHandler.getResult();

        boolean hasConflictDetected = false;
        boolean hasOfferAccepted = true;
        List<ClientLocation> acceptedAndInNeedClients = new ArrayList<>();

        for (FileOfferResponse response : result.getFileOfferResponses()) {
            if (response.hasConflict()) {
                hasConflictDetected = true;
                // no need to check other responses too, we need a conflict file anyway
                break;
            }

            if (! response.hasAcceptedOffer()) {
                hasOfferAccepted = false;
                // we need to reschedule for all clients
                break;
            }

            if (! response.isRequestObsolete()) {
                // we need to send the request to this client
                acceptedAndInNeedClients.add(new ClientLocation(
                        response.getClientDevice().getClientDeviceId(),
                        response.getClientDevice().getPeerAddress()
                ));
            }
        }

        if (hasConflictDetected) {
            // all clients will have to again check for the conflict file
            ConflictHandler.createConflictFile(
                    this.globalEventBus,
                    this.clientDevice.getClientDeviceId().toString(),
                    this.objectStore,
                    this.storageAdapter,
                    new LocalPathElement(event.getPath().toString())
            );
            return;
        }

        if (! hasOfferAccepted) {
            logger.info("Rescheduling event " + event.getEventName() + " for file " + event.getPath().toString());
            this.globalEventBus.publish(new CreateBusEvent(
                    event
            ));
            return;
        }

        // Now we can start to send the event to all clients which need it

        ANetworkHandler exchangeHandler;
        Thread exchangeHandlerThread;

        if (event instanceof DeleteEvent) {
            exchangeHandler = new FileDeleteExchangeHandler(
                    fileExchangeId,
                    this.clientDevice,
                    this.storageAdapter,
                    this.clientManager,
                    this.client,
                    this.objectStore,
                    this.globalEventBus,
                    acceptedAndInNeedClients,
                    (DeleteEvent) event
            );
            logger.debug("Starting fileDelete handler for exchangeId " + fileExchangeId);

            exchangeHandlerThread = new Thread(exchangeHandler);
            exchangeHandlerThread.setName("FileDeleteExchangeHandler-" + fileExchangeId);
        } else if (event instanceof MoveEvent) {
            exchangeHandler = new FileMoveExchangeHandler(
                    fileExchangeId,
                    this.clientDevice,
                    this.storageAdapter,
                    this.clientManager,
                    this.client,
                    this.globalEventBus,
                    acceptedAndInNeedClients,
                    (MoveEvent) event
            );

            logger.debug("Starting fileMove handler for exchangeId " + fileExchangeId);
            exchangeHandlerThread = new Thread(exchangeHandler);
            exchangeHandlerThread.setName("MoveEventExchangeHandler-" + fileExchangeId);
        } else {
            exchangeHandler = new FilePushExchangeHandler(
                    fileExchangeId,
                    this.clientDevice,
                    this.storageAdapter,
                    this.clientManager,
                    this.client,
                    this.objectStore,
                    acceptedAndInNeedClients,
                    event.getPath().toString()
            );

            logger.debug("Starting filePush handler for exchangeId " + fileExchangeId);
            exchangeHandlerThread = new Thread(exchangeHandler);
            exchangeHandlerThread.setName("FilePushExchangeHandler-" + fileExchangeId);
        }

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, exchangeHandler);
        exchangeHandlerThread.start();

        logger.debug("Waiting for exchange " + fileExchangeId + " to complete...");
        try {
            exchangeHandler.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(fileExchangeId);

        if (! exchangeHandler.isCompleted()) {
            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file push");
            return;
        }

        Object exchangeHandlerResult = exchangeHandler.getResult();
        logger.info("Result of exchange " + fileExchangeId + " is " + exchangeHandlerResult.toString());
        logger.trace("Sync duration for request " + fileExchangeId + " was: " + (System.currentTimeMillis() - start) + "ms");
    }
}
