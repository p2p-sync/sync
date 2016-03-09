package org.rmatil.sync.core.syncer.file;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.ConflictHandler;
import org.rmatil.sync.core.api.IFileSyncer;
import org.rmatil.sync.core.eventbus.CleanModifyIgnoreEventsBusEvent;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Initializes the file offering protocol for each local file event which
 * has been passed to the syncer
 */
public class FileSyncer implements IFileSyncer {

    protected static final Logger logger = LoggerFactory.getLogger(FileSyncer.class);

    public static final int NUMBER_OF_SYNCS = 1;

    protected       IUser               user;
    protected       INode               node;
    protected       INodeManager        nodeManager;
    protected       ITreeStorageAdapter storageAdapter;
    protected       IObjectStore        objectStore;
    protected final List<IEvent>        eventsToIgnore;

    protected MBassador<IBusEvent> globalEventBus;
    protected ExecutorService      syncExecutor;

    protected ClientDevice clientDevice;

    public FileSyncer(IUser user, INode node, INodeManager nodeManager, ITreeStorageAdapter storageAdapter, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus) {
        this.user = user;
        this.node = node;
        this.nodeManager = nodeManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;

        this.clientDevice = new ClientDevice(user.getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress());

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

    @Handler
    public void handleCleanIgnoreEvents(CleanModifyIgnoreEventsBusEvent event) {
        logger.debug("Got clean up ignore events event from global event bus for file " + event.getRelativePath());
        synchronized (this.eventsToIgnore) {
            Iterator<IEvent> itr = this.eventsToIgnore.iterator();
            while (itr.hasNext()) {
                IEvent ev = itr.next();
                if (ev.getPath().toString().equals(event.getRelativePath()) &&
                        ev instanceof ModifyEvent) {
                    logger.trace("Remove modify event " + ev.getEventName());
                    itr.remove();
                }
            }
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

                    logger.info("Ignoring (" + this.toString() + ") syncing of event " + event.getEventName() + " for path " + event.getPath().toString() + " on client " + this.node.getPeerAddress().inetAddress().getHostName() + ":" + this.node.getPeerAddress().tcpPort() + ")");
                    this.eventsToIgnore.remove(eventToCheck);
                    return;
                }
            }
        }

        if (event instanceof ModifyEvent) {
            // directory modifications will happen, since we need them to correctly identify
            // a move of a directory.
            try {
                if (this.storageAdapter.isDir(new TreePathElement(event.getPath().toString()))) {
                    logger.info("Skipping received modified event for directory " + event.getPath().toString() + " on client " + this.node.getPeerAddress().inetAddress().getHostName() + ":" + this.node.getPeerAddress().tcpPort() + ")");
                    return;
                }
            } catch (InputOutputException e) {
                logger.error("Failed to check whether the modify event for " + event.getPath().toString() + " should be ignored. Therefore will sync...");
            }
        }

        // check, whether there is a fileId already present,
        // e.g. made in an earlier push request (or on another client)
        try {
            if (null == this.node.getIdentifierManager().getValue(event.getPath().toString())) {
                // add a file id
                this.node.getIdentifierManager().addIdentifier(event.getPath().toString(), UUID.randomUUID());
            }
        } catch (InputOutputException e) {
            throw new SyncFailedException("Failed to add a new file id for path " + event.getPath(), e);
        }

        logger.debug("Syncing event " + event.getEventName() + " for path " + event.getPath().toString() + " on client " + this.node.getPeerAddress().inetAddress().getHostName() + ":" + this.node.getPeerAddress().tcpPort() + ")");

        UUID fileExchangeId = UUID.randomUUID();

        // all events require an offering step
        FileOfferExchangeHandler fileOfferExchangeHandler = new FileOfferExchangeHandler(
                fileExchangeId,
                this.clientDevice,
                this.nodeManager,
                this.node,
                this.objectStore,
                this.storageAdapter,
                this.globalEventBus,
                event
        );

        logger.debug("Starting file offer exchange handler for exchangeId " + fileExchangeId);

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, fileOfferExchangeHandler);
        Thread fileOfferExchangeHandlerThread = new Thread(fileOfferExchangeHandler);
        fileOfferExchangeHandlerThread.setName("FileOfferExchangeHandler-" + fileExchangeId);
        fileOfferExchangeHandlerThread.start();

        logger.debug("Waiting for offer exchange " + fileExchangeId + " to complete... (Max. " + FileOfferExchangeHandler.MAX_WAITING_TIME + "ms)");
        try {
            fileOfferExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Failed to await for file offer exchange " + fileExchangeId + ". Message: " + e.getMessage());
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(fileExchangeId);

        if (! fileOfferExchangeHandler.isCompleted()) {
            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file offering");
            return;
        }

        FileOfferExchangeHandlerResult result = fileOfferExchangeHandler.getResult();

        boolean hasConflictDetected = false;
        boolean hasOfferAccepted = true;
        List<NodeLocation> acceptedAndInNeedClients = new ArrayList<>();

        for (FileOfferResponse response : result.getFileOfferResponses()) {
            if (StatusCode.CONFLICT.equals(response.getStatusCode())) {
                hasConflictDetected = true;
                // no need to check other responses too, we need a conflict file anyway
                break;
            }

            if (StatusCode.DENIED.equals(response.getStatusCode())) {
                hasOfferAccepted = false;
                // we need to reschedule for all clients
                break;
            }

            if (StatusCode.ACCEPTED.equals(response.getStatusCode())) {
                // we need to send the request to this client
                acceptedAndInNeedClients.add(new NodeLocation(
                        response.getClientDevice().getUserName(),
                        response.getClientDevice().getClientDeviceId(),
                        response.getClientDevice().getPeerAddress()
                ));
            }
        }

        if (hasConflictDetected) {
            // all clients will have to check again for the conflict file
            Path conflictFile = ConflictHandler.createConflictFile(
                    this.globalEventBus,
                    this.clientDevice.getClientDeviceId().toString(),
                    this.objectStore,
                    this.storageAdapter,
                    new TreePathElement(event.getPath().toString())
            );

            // move element in the IdentifierManager too
            UUID fileId = null;
            try {
                fileId = this.node.getIdentifierManager().getValue(event.getPath().toString());
                if (null != conflictFile && null != fileId) {
                    this.node.getIdentifierManager().moveKey(event.getPath().toString(), conflictFile.toString());
                }
            } catch (InputOutputException e) {
                logger.warn("Failed to move conflicting file with id " + fileId + " on path " + event.getPath().toString() + " to new path too. Message: " + e.getMessage());
            }

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
                    this.nodeManager,
                    this.node,
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
                    this.nodeManager,
                    this.node,
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
                    this.nodeManager,
                    this.node,
                    this.objectStore,
                    acceptedAndInNeedClients,
                    event.getPath().toString()
            );

            logger.debug("Starting filePush handler for exchangeId " + fileExchangeId);
            exchangeHandlerThread = new Thread(exchangeHandler);
            exchangeHandlerThread.setName("FilePushExchangeHandler-" + fileExchangeId);
        }

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, exchangeHandler);
        exchangeHandlerThread.start();

        logger.debug("Waiting for exchange " + fileExchangeId + " to complete...");
        try {
            exchangeHandler.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(fileExchangeId);

        if (! exchangeHandler.isCompleted()) {
            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file sync");
            return;
        }

        Object exchangeHandlerResult = exchangeHandler.getResult();
        logger.info("Result of exchange " + fileExchangeId + " is " + exchangeHandlerResult.toString());
        logger.trace("Sync duration for request " + fileExchangeId + " was: " + (System.currentTimeMillis() - start) + "ms");
    }
}
