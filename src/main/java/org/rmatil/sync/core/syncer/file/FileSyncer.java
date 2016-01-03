package org.rmatil.sync.core.syncer.file;

import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.syncer.ISyncer;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
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

    protected ExecutorService syncExecutor;

    public FileSyncer(IUser user, IClient client, ClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore, List<IEvent> eventsToIgnore) {
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;

        syncExecutor = Executors.newFixedThreadPool(NUMBER_OF_SYNCS);

        this.eventsToIgnore = eventsToIgnore;
    }

    @Override
    public void sync(IEvent event)
            throws SyncFailedException {

        logger.debug("Syncing event " + event.getEventName() + " for path " + event.getPath().toString());


        ClientDevice clientDevice = new ClientDevice(
                this.user.getUserName(),
                this.client.getClientDeviceId(),
                this.client.getPeerAddress()
        );

        UUID fileExchangeId = UUID.randomUUID();

        FileOfferExchangeHandler fileOfferExchangeHandler = new FileOfferExchangeHandler(
                fileExchangeId,
                clientDevice,
                this.clientManager,
                this.client,
                event
        );

        logger.debug("Starting fileExchange handler for exchangeId " + fileExchangeId);

        this.client.getObjectDataReplyHandler().addCallbackHandler(fileExchangeId, fileOfferExchangeHandler);

        new Thread(fileOfferExchangeHandler).start();

        try {
            Thread.sleep(100L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            logger.debug("Waiting for offer exchange " + fileExchangeId + " to complete...");
            fileOfferExchangeHandler.await();
            logger.debug("Offer exchange " + fileExchangeId + " completed");

            if (fileOfferExchangeHandler.isCompleted()) {
                logger.info("Result of file offering " + fileExchangeId + " is " + fileOfferExchangeHandler.getResult().toString());
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // TODO: exchange file chunks on success
        // TODO: maybe resend request if any client did not accept
    }
}
