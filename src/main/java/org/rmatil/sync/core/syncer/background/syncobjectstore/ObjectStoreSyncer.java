package org.rmatil.sync.core.syncer.background.syncobjectstore;

import org.rmatil.sync.core.StringLengthComparator;
import org.rmatil.sync.core.Zip;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandlerResult;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteExchangeHandler;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteExchangeHandlerResult;
import org.rmatil.sync.core.syncer.background.syncresult.SyncResultExchangeHandler;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ObjectStoreSyncer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ObjectStoreSyncer.class);

    protected IClient client;

    protected IClientManager clientManager;

    protected IObjectStore objectStore;

    protected IStorageAdapter storageAdapter;

    protected UUID exchangeId;

    public ObjectStoreSyncer(IClient client, IClientManager clientManager, IObjectStore objectStore, IStorageAdapter storageAdapter, UUID exchangeId) {
        this.client = client;
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void run() {
        try {
            FetchObjectStoreExchangeHandler fetchObjectStoreExchangeHandler = new FetchObjectStoreExchangeHandler(
                    this.client,
                    this.clientManager,
                    this.exchangeId
            );

            Thread fetchObjectStoreExchangeHandlerThread = new Thread(fetchObjectStoreExchangeHandler);
            fetchObjectStoreExchangeHandlerThread.setName("FetchObjectStoreExchangeHandler-" +this.exchangeId);
            fetchObjectStoreExchangeHandlerThread.start();

            try {
                fetchObjectStoreExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for FetchObjectStoreExchangeHandler. Message: " + e.getMessage());
            }

            if (!fetchObjectStoreExchangeHandler.isCompleted()) {
                logger.error("FetchObjectStoreExchangeHandler should be completed after waiting. Aborting syncing of object store");
                return;
            }

            FetchObjectStoreExchangeHandlerResult result = fetchObjectStoreExchangeHandler.getResult();

            // unzip object stores
            Map<ClientDevice, IObjectStore> objectStores = Zip.unzipObjectStore(this.objectStore, result);

            // use a tree map for guaranteed ordering
            Map<String, ClientDevice> deletedPaths = new TreeMap<>(new StringLengthComparator());
            Map<String, ClientDevice> updatedPaths = new TreeMap<>(new StringLengthComparator());

            for (Map.Entry<ClientDevice, IObjectStore> entry : objectStores.entrySet()) {
                HashMap<ObjectStore.MergedObjectType, Set<String>> outdatedOrDeletedPaths = this.objectStore.mergeObjectStore(entry.getValue());

                for (String outDatedPath : outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CHANGED)) {
                    updatedPaths.put(outDatedPath, entry.getKey());
                }

                for (String deletedPath : outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.DELETED)) {
                    deletedPaths.put(deletedPath, entry.getKey());
                }

                entry.getValue().getObjectManager().getStorageAdapater().delete(new LocalPathElement("./"));
            }

            logger.info("Zipping merged object store");
            byte[] zippedObjectStore = Zip.zipObjectStore(this.objectStore);

            // delete all removed files
            for (Map.Entry<String, ClientDevice> entry : deletedPaths.entrySet()) {
                logger.debug("Removing deleted path " + entry.getKey());
                this.storageAdapter.delete(new LocalPathElement(entry.getKey()));
            }

            // fetch all missing files
            logger.info("Fetching all missing " + updatedPaths.size() + " files");

            for (Map.Entry<String, ClientDevice> entry : updatedPaths.entrySet()) {
                UUID exchangeId = UUID.randomUUID();
                logger.debug("Starting to fetch file " + entry.getKey() + " with exchangeId " + exchangeId);

                FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                        this.storageAdapter,
                        this.client,
                        this.clientManager,
                        new ClientLocation(
                                entry.getValue().getClientDeviceId(),
                                entry.getValue().getPeerAddress()
                        ),
                        entry.getKey(),
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

            logger.info("Notifying other clients about object store merge results");

            SyncResultExchangeHandler syncResultExchangeHandler = new SyncResultExchangeHandler(
                    this.exchangeId,
                    this.clientManager,
                    this.client,
                    zippedObjectStore
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(this.exchangeId, syncResultExchangeHandler);

            Thread syncResultsExchangeHandlerThread = new Thread(syncResultExchangeHandler);
            syncResultsExchangeHandlerThread.setName("SyncResultExchangeHandler-" + this.exchangeId);
            syncResultsExchangeHandlerThread.start();

            logger.info("Awaiting for the other clients to complete fetching missing or outdated files...");

            // await here for completion of all clients
            try {
                // wait a maximum of 5000ms for each file
                long timeToWait = updatedPaths.size() * 5000L;

                syncResultExchangeHandler.await(timeToWait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for syncing of merged object store is complete on the other clients. Message: " + e.getMessage(), e);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(this.exchangeId);

            if (! syncResultExchangeHandler.isCompleted()) {
                logger.error("SyncResultExchangeHandler should be completed after awaiting. Never starting file aggregators again until next sync...");
                return;
            }

            logger.info("Starting to send a sync complete exchange to restart event aggregation on all clients...");

            // start a sync complete exchange handler
            SyncCompleteExchangeHandler syncCompleteExchangeHandler = new SyncCompleteExchangeHandler(
                    this.client,
                    this.exchangeId
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(this.exchangeId, syncCompleteExchangeHandler);

            Thread syncCompleteExchangeHandlerThread = new Thread(syncCompleteExchangeHandler);
            syncCompleteExchangeHandlerThread.setName("SyncCompleteExchangeHandler-" + this.exchangeId);
            syncCompleteExchangeHandlerThread.start();

            try {
                syncCompleteExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for the other clients to start their event aggregators. Message: " + e.getMessage(), e);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(this.exchangeId);

            if (! syncCompleteExchangeHandler.isCompleted()) {
                logger.error("SyncCompleteExchangeHandler should be completed after awaiting. There might be clients which started their event aggregation again, but some didn't. Expect inconsistent results until next sync.");
                return;
            }

            logger.info("All event aggregators should be running again now");
            SyncCompleteExchangeHandlerResult completeExchangeHandlerResult = syncCompleteExchangeHandler.getResult();

        } catch (Exception e) {
            logger.error("Got exception in ObjectStoreSyncer. Message: " + e.getMessage(), e);
        }
    }

}
