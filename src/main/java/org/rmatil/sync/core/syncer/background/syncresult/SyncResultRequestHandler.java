package org.rmatil.sync.core.syncer.background.syncresult;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.StringLengthComparator;
import org.rmatil.sync.core.Zip;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.IExtendedLocalStateRequestCallback;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SyncResultRequestHandler implements IExtendedLocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(SyncResultRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected IClientManager clientManager;
    protected IEventAggregator eventAggregator;
    protected SyncResultRequest    request;
    protected MBassador<IBusEvent> globalEventBus;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    @Override
    public void setClientManager(IClientManager clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public void setEventAggregator(IEventAggregator eventAggregator) {
        this.eventAggregator = eventAggregator;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof SyncResultRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + SyncResultRequest.class.getName());
        }

        this.request = (SyncResultRequest) iRequest;
    }

    @Override
    public void run() {
        try {

            byte[] zippedObjectStore = this.request.getZippedObjectStore();

            IStorageAdapter objectStoreStorageAdapter = this.objectStore.getObjectManager().getStorageAdapater();
            if (objectStoreStorageAdapter.exists(StorageType.DIRECTORY, new LocalPathElement("slaveMergeResultObjectStore"))) {
                objectStoreStorageAdapter.delete(new LocalPathElement("slaveMergeResultObjectStore"));
            }

            IObjectStore receivedObjectStore = Zip.unzipObjectStore(this.objectStore, "slaveMergeResultObjectStore", zippedObjectStore);

            if (null == receivedObjectStore) {
                logger.error("Could not unzip merged object store. Aborting removing/fetching files...");
                return;
            }

            Set<String> deletedPaths = new TreeSet<>(new StringLengthComparator());
            Set<String> updatedPaths = new TreeSet<>(new StringLengthComparator());
            HashMap<ObjectStore.MergedObjectType, Set<String>> outdatedOrDeletedPaths = this.objectStore.mergeObjectStore(receivedObjectStore);

            updatedPaths.addAll(outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CHANGED));
            deletedPaths.addAll(outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.DELETED));

            logger.info("Removing all deleted " + deletedPaths.size() + " files");
            for (String entry : deletedPaths) {
                logger.info("Removing " + entry + " from disk after merging object store");
                this.storageAdapter.delete(new LocalPathElement(entry));
            }

            // fetch all missing files
            logger.info("Fetching all missing " + updatedPaths.size() + " files");

            for (String entry : updatedPaths) {
                UUID exchangeId = UUID.randomUUID();
                logger.debug("Starting to fetch file " + entry + " with exchangeId " + exchangeId);

                // before updating, check the actual content hash on disk
                // to prevent data loss during sync
                PathObject mergedPathObject = this.objectStore.getObjectManager().getObjectForPath(entry);
                Version lastVersion = mergedPathObject.getVersions().get(Math.max(0, mergedPathObject.getVersions().size() - 1));
                // use the temp object store to resync the file. If the state is different
                // in the object store than on disk, a ModifyEvent is propagated in SyncCompleteRequestHandler
                receivedObjectStore.syncFile(this.storageAdapter.getRootDir().resolve(mergedPathObject.getAbsolutePath()).toFile());
                PathObject modifiedPathObject = receivedObjectStore.getObjectManager().getObjectForPath(entry);
                Version modifiedLastVersion = modifiedPathObject.getVersions().get(Math.max(0, modifiedPathObject.getVersions().size() - 1));

                if (! modifiedLastVersion.equals(lastVersion)) {
                    // we just changed the file on this client while syncing...
                    // therefore we use this state and do not request an outdated state from another client
                    logger.info("Detected file change while merging object store... Using our state");
                    continue;
                }

                FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                        this.storageAdapter,
                        this.client,
                        this.clientManager,
                        new ClientLocation(
                                this.request.getClientDevice().getClientDeviceId(),
                                this.request.getClientDevice().getPeerAddress()
                        ),
                        entry,
                        exchangeId
                );

                this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, fileDemandExchangeHandler);

                Thread fileDemandExchangeHandlerThread = new Thread(fileDemandExchangeHandler);
                fileDemandExchangeHandlerThread.setName("FileDemandExchangeHandlerThread-" + exchangeId);
                fileDemandExchangeHandlerThread.start();

                try {
                    fileDemandExchangeHandler.await();
                } catch (Exception e) {
                    logger.error("Got interrupted while waiting for fileDemandExchangeHandler " + exchangeId + " to complete. Message: " + e.getMessage());
                }

                if (! fileDemandExchangeHandler.isCompleted()) {
                    logger.error("FileDemandExchangeHandler " + exchangeId + " should be completed after wait.");
                }
            }

            // remove object store again
            receivedObjectStore.getObjectManager().getStorageAdapater().delete(new LocalPathElement("./"));
            objectStoreStorageAdapter.delete(new LocalPathElement("slaveMergeResultObjectStore"));

            IResponse syncResultRespone = new SyncResultResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress())
            );

            this.sendResponse(syncResultRespone);

        } catch (Exception e) {
            logger.error("Got exception in SyncResultRequestHandler. Message: " + e.getMessage(), e);
        }
    }


    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    public void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }
}
