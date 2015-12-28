package org.rmatil.sync.core.syncer.file;

import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.FileExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.FileExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponseHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferType;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.core.syncer.ISyncer;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
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
    protected FileOfferResponseHandler fileOfferResponseHandler;

    protected CompletionService<FileExchangeHandlerResult> completionService;
    protected List<IEvent>                                 eventsToIgnore;

    public FileSyncer(IUser user, IClient client, ClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore, FileOfferResponseHandler fileOfferResponseHandler) {
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.fileOfferResponseHandler = fileOfferResponseHandler;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        this.completionService = new ExecutorCompletionService<>(executorService);

        this.eventsToIgnore = new ArrayList<>();
    }

    @Override
    public void sync(IEvent event)
            throws SyncFailedException {

        // fetch all results until none is available anymore
        Future<FileExchangeHandlerResult> futureResult = this.completionService.poll();
        while (null != futureResult) {
            try {
                FileExchangeHandlerResult result = futureResult.get();
                this.eventsToIgnore.add(result.getResultEvent());

                // finally unregister the exchange handler after task has been completed
                this.fileOfferResponseHandler.unregisterFileExchangeHandler(result.getFileExchangeId());
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Failed to add future ignored task. Message: " + e.getMessage(), e);
            }

            futureResult = this.completionService.poll();
        }

        if (this.eventsToIgnore.contains(event)) {
            this.eventsToIgnore.remove(event);

            return;
        }

        PathObject pathObject;
        try {
            pathObject = this.objectStore.getObjectManager().getObject(event.getPath().toString());
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

        FileExchangeHandler fileExchangeHandler = new FileExchangeHandler(
                fileExchangeId,
                clientDevice,
                this.storageAdapter,
                this.user,
                this.clientManager,
                this.client,
                fileOfferRequest
        );

        // register file exchange handler for this file exchange to
        // forward incoming responses correctly
        this.fileOfferResponseHandler.registerFileExchangeHandler(fileExchangeId, fileExchangeHandler);

        this.completionService.submit(fileExchangeHandler);
    }
}
