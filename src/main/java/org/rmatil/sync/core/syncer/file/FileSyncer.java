package org.rmatil.sync.core.syncer.file;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.core.syncer.ISyncer;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Initializes the file offering protocol for each local file event which
 * has been passed to the syncer
 */
public class FileSyncer implements ISyncer {

    protected static final Logger logger = LoggerFactory.getLogger(FileSyncer.class);

    public static final int NUMBER_OF_SYNCS = 25;

    protected       IUser           user;
    protected       IClient         client;
    protected       ClientManager   clientManager;
    protected       IStorageAdapter storageAdapter;
    protected       IObjectStore    objectStore;
    protected final List<IEvent>    eventsToIgnore;

    protected MBassador<IBusEvent> globalEventBus;
    protected ExecutorService      syncExecutor;

    protected ClientDevice clientDevice;

    public FileSyncer(IUser user, IClient client, ClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus) {
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
        logger.debug("Got notified from event bus: " + event.getEvent().getEventName() + " for file " + event.getEvent().getPath().toString());
        synchronized (this.eventsToIgnore) {
            this.eventsToIgnore.add(event.getEvent());
        }
    }

    @Override
    public void sync(IEvent event)
            throws SyncFailedException {

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

        logger.debug("Syncing event " + event.getEventName() + " for path " + event.getPath().toString());

        UUID fileExchangeId = UUID.randomUUID();

        FileOfferExchangeHandler fileOfferExchangeHandler = new FileOfferExchangeHandler(
                fileExchangeId,
                this.clientDevice,
                this.clientManager,
                this.client,
                this.objectStore,
                event
        );

        logger.debug("Starting fileExchange handler for exchangeId " + fileExchangeId);

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, fileOfferExchangeHandler);

        new Thread(fileOfferExchangeHandler).start();

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        logger.debug("Waiting for offer exchange " + fileExchangeId + " to complete... (Max. " + FileOfferExchangeHandler.MAX_WAITING_TIME + "ms)");
        try {
            fileOfferExchangeHandler.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.debug("Offer exchange " + fileExchangeId + " completed");

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(fileExchangeId);

        if (! fileOfferExchangeHandler.isCompleted()) {
            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file offering");
            return;
        }

        FileOfferExchangeHandlerResult result = fileOfferExchangeHandler.getResult();
        logger.info("Result of file offering " + fileExchangeId + " is " + result.toString());


        if (result.hasConflictDetected()) {
            this.createConflictFile(new LocalPathElement(event.getPath().toString()));
            return;
        } else if (! result.hasOfferAccepted()) {
            return;
        }

        // TODO: what if client did not accept request
        // TODO: maybe resend request if any client did not accept

        // Now we can start to send the file

        FilePushExchangeHandler filePushRequestHandler = new FilePushExchangeHandler(
                fileExchangeId,
                this.clientDevice,
                this.storageAdapter,
                this.clientManager,
                this.client,
                event.getPath().toString()
        );

        logger.debug("Starting filePush handler for exchangeId " + fileExchangeId);

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(fileExchangeId, filePushRequestHandler);
        new Thread(filePushRequestHandler).start();

        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


//        logger.debug("Waiting for push exchange " + fileExchangeId + " to complete...");
//        try {
//            filePushRequestHandler.await();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        logger.debug("Push exchange " + fileExchangeId + " completed");
//
//        this.client.getObjectDataReplyHandler().removeCallbackHandler(fileExchangeId);
//
//        if (! filePushRequestHandler.isCompleted()) {
//            logger.error("No result received from clients for request " + fileExchangeId + ". Aborting file push");
//            return;
//        }
//
//        FilePushExchangeHandlerResult result2 = filePushRequestHandler.getResult();
//        logger.info("Result of file push " + fileExchangeId + " is " + result2.toString());

    }

    protected void createConflictFile(LocalPathElement pathElement) {
        PathObject pathObject;
        try {
            Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
            String hash = indexPaths.get(pathElement.getPath());

            pathObject = this.objectStore.getObjectManager().getObject(hash);
        } catch (InputOutputException e) {
            logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened");
            return;
        }

        // compare local and remote file versions
        List<Version> localFileVersions = pathObject.getVersions();
        Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
        String lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

        Path conflictFilePath;
        try {
            IFileMetaInfo fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
            conflictFilePath = Paths.get(Naming.getConflictFileName(pathElement.getPath(), true, fileMetaInfo.getFileExtension(), this.clientDevice.getClientDeviceId().toString()));
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new MoveEvent(
                            Paths.get(pathElement.getPath()),
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));
            this.globalEventBus.publish(new CreateBusEvent(
                    new CreateEvent(
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));

        } catch (InputOutputException e) {
            logger.error("Can not read meta information for file " + pathElement.getPath() + ". Moving the conflict file failed");
            return;
        }

        try {
            this.storageAdapter.move(StorageType.FILE, pathElement, new LocalPathElement(conflictFilePath.toString()));
        } catch (InputOutputException e) {
            logger.error("Can not move conflict file " + pathElement.getPath() + " to " + conflictFilePath.toString() + ". Message: " + e.getMessage());
        }
    }
}
