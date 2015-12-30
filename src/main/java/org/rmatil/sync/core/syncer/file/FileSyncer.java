package org.rmatil.sync.core.syncer.file;

import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferType;
import org.rmatil.sync.core.syncer.ISyncer;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Initializes the file offering protocol for each local file event which
 * has been passed to the syncer
 */
public class FileSyncer implements ISyncer {

    protected static final Logger logger = LoggerFactory.getLogger(FileSyncer.class);

    protected IUser                    user;
    protected IClient                  client;
    protected ClientManager            clientManager;
    protected IStorageAdapter          storageAdapter;
    protected IObjectStore             objectStore;

    protected CompletionService<FileOfferExchangeHandlerResult> completionService;
    protected List<IEvent>                                      eventsToIgnore;

    public FileSyncer(IUser user, IClient client, ClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore) {
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        this.completionService = new ExecutorCompletionService<>(executorService);

        this.eventsToIgnore = new ArrayList<>();
    }

    @Override
    public void sync(IEvent event)
            throws SyncFailedException {

        logger.debug("Syncing event " + event.getEventName() + " for path " + event.getPath().toString());

        // fetch all results until none is available anymore
        Future<FileOfferExchangeHandlerResult> futureResult = this.completionService.poll();
        while (null != futureResult) {
            try {
                FileOfferExchangeHandlerResult result = futureResult.get();
                this.eventsToIgnore.add(result.getResultEvent());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add future ignored task. Message: " + e.getMessage(), e);
            }

            futureResult = this.completionService.poll();
        }

        // TODO: a list should be returned too (create & delete event), because sometimes the event aggregation is wrong
        // remove the event of moving the conflict file if it exists
        if (this.eventsToIgnore.contains(event)) {
            this.eventsToIgnore.remove(event);
            logger.info("Ignoring event " + event.getEventName() + " for conflict file " + event.getPath().toString());
            return;
        }

        PathObject pathObject;
        try {
            Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
            String hash = indexPaths.get(event.getPath().toString());

            pathObject = this.objectStore.getObjectManager().getObject(hash);
        } catch (InputOutputException e) {
            throw new SyncFailedException("Failed to read path object from object store. Message: " + e.getMessage(), e);
        }


        // create event does not have versions in it but the original hash,
        // we do not care for versions if we send a delete request
        List<Version> fileVersions = new ArrayList<>();
        if (event instanceof ModifyEvent || event instanceof MoveEvent) {
            fileVersions = pathObject.getVersions();
        }

        List<Sharer> fileSharers = pathObject.getSharers();


        FileOfferType fileOfferType = null;

        switch (event.getEventName()) {
            case CreateEvent.EVENT_NAME:
                fileOfferType = FileOfferType.CREATE;
                break;
            case ModifyEvent.EVENT_NAME:
                fileOfferType = FileOfferType.MODIFY;
                break;
            case MoveEvent.EVENT_NAME:
                fileOfferType = FileOfferType.MOVE;
                break;
            case DeleteEvent.EVENT_NAME:
                fileOfferType = FileOfferType.DELETE;
                break;
        }


        ClientDevice clientDevice = new ClientDevice(
                this.user.getUserName(),
                this.client.getClientDeviceId(),
                this.client.getPeerAddress()
        );

        UUID fileExchangeId = UUID.randomUUID();

        FileOfferRequest fileOfferRequest = new FileOfferRequest(
                fileExchangeId,
                clientDevice,
                fileVersions,
                fileSharers,
                event.getPath().toString(),
                fileOfferType
        );

        FileOfferExchangeHandler fileOfferExchangeHandler = new FileOfferExchangeHandler(
                fileExchangeId,
                clientDevice,
                this.storageAdapter,
                this.user,
                this.clientManager,
                this.client,
                fileOfferRequest
        );

        logger.debug("Starting fileExchange handler for fileExchangeId " + fileExchangeId);

        this.completionService.submit(fileOfferExchangeHandler);
    }
}
