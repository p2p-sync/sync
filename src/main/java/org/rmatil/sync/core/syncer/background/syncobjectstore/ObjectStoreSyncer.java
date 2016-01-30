package org.rmatil.sync.core.syncer.background.syncobjectstore;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.StringLengthComparator;
import org.rmatil.sync.core.Zip;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandlerResult;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteExchangeHandler;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteExchangeHandlerResult;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteRequest;
import org.rmatil.sync.core.syncer.background.syncresult.SyncResultExchangeHandler;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * This class actually reconciles the object stores and the synchronized
 * folders among all clients. The following steps are traversed to accomplish this task:
 * </p>
 * <ol>
 * <li>Fetch all object stores from all other clients
 * ({@link FetchObjectStoreExchangeHandler})</li>
 * <li>Compare the object stores and remove obsolete files
 * resp. fetch missing files from the corresponding clients
 * ({@link FileDemandExchangeHandler})</li>
 * <li>After having established a merged object store and
 * synchronized folder, send the merged object store to all
 * other clients ({@link SyncResultExchangeHandler})</li>
 * <li>All notified clients will then compare their object
 * store with the received one and also remove deleted paths
 * resp. fetch missing ones ({@link FileDemandExchangeHandler})</li>
 * <li>After having completed the synchronization on all
 * clients, the {@link ObjectStoreSyncer} will send a {@link SyncCompleteRequest} to all clients, which restart their event aggregator and publish changes made in the mean time</li>
 * </ol>
 */
public class ObjectStoreSyncer implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(ObjectStoreSyncer.class);

    /**
     * The client to send messages
     */
    protected IClient client;

    /**
     * The client manager to fetch all locations from
     */
    protected IClientManager clientManager;

    /**
     * The object store which has to been merged with the ones of the other clients
     */
    protected IObjectStore objectStore;

    /**
     * The storage adapter to the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    protected IEventAggregator eventAggregator;

    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The exchange id of the master election and init sync step
     */
    protected UUID exchangeId;

    public ObjectStoreSyncer(IClient client, IClientManager clientManager, IObjectStore objectStore, IStorageAdapter storageAdapter, IEventAggregator eventAggregator, MBassador<IBusEvent> globalEventBus, UUID exchangeId) {
        this.client = client;
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
        this.eventAggregator = eventAggregator;
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void run() {
        try {
            UUID subExchangeId = UUID.randomUUID();
            logger.info("Starting ObjectStoreSyncer for request " + this.exchangeId + " as subprocess with id " + subExchangeId);

            FetchObjectStoreExchangeHandler fetchObjectStoreExchangeHandler = new FetchObjectStoreExchangeHandler(
                    this.client,
                    this.clientManager,
                    subExchangeId
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(subExchangeId, fetchObjectStoreExchangeHandler);

            Thread fetchObjectStoreExchangeHandlerThread = new Thread(fetchObjectStoreExchangeHandler);
            fetchObjectStoreExchangeHandlerThread.setName("FetchObjectStoreExchangeHandler-" +subExchangeId);
            fetchObjectStoreExchangeHandlerThread.start();

            try {
                fetchObjectStoreExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for FetchObjectStoreExchangeHandler. Message: " + e.getMessage());
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(subExchangeId);

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
                this.objectStore.getObjectManager().getStorageAdapater().delete(new LocalPathElement(entry.getKey().getClientDeviceId().toString()));
            }

            logger.info("Zipping merged object store");
            byte[] zippedObjectStore = Zip.zipObjectStore(this.objectStore);

            // delete all removed files
            logger.info("Removing all (" + deletedPaths.size() + ") deleted files");
            for (Map.Entry<String, ClientDevice> entry : deletedPaths.entrySet()) {
                logger.debug("Removing deleted path " + entry.getKey());
                this.storageAdapter.delete(new LocalPathElement(entry.getKey()));
            }

            // fetch all missing files
            logger.info("Fetching all (" + updatedPaths.size() + ") missing files");

            for (Map.Entry<String, ClientDevice> entry : updatedPaths.entrySet()) {
                UUID exchangeId = UUID.randomUUID();
                logger.debug("Starting to fetch file " + entry.getKey() + " with exchangeId " + exchangeId);

                // before updating, check the actual content hash on disk
                // to prevent data loss during sync
                PathObject mergedPathObject = this.objectStore.getObjectManager().getObjectForPath(entry.getKey());
                Version lastVersion = mergedPathObject.getVersions().get(Math.max(0, mergedPathObject.getVersions().size() - 1));
                this.objectStore.syncFile(this.storageAdapter.getRootDir().resolve(mergedPathObject.getAbsolutePath()).toFile());
                PathObject modifiedPathObject = this.objectStore.getObjectManager().getObjectForPath(entry.getKey());
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
                        this.globalEventBus,
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
                } catch (InterruptedException e) {
                    logger.error("Got interrupted while waiting for fileDemandExchangeHandler " + exchangeId + " to complete. Message: " + e.getMessage());
                }

                this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(subExchangeId);

                if (! fileDemandExchangeHandler.isCompleted()) {
                    logger.error("FileDemandExchangeHandler " + exchangeId + " should be completed after wait.");
                }
            }

            logger.info("Notifying other clients about object store merge results");

            SyncResultExchangeHandler syncResultExchangeHandler = new SyncResultExchangeHandler(
                    subExchangeId,
                    this.clientManager,
                    this.client,
                    zippedObjectStore
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(subExchangeId, syncResultExchangeHandler);

            Thread syncResultsExchangeHandlerThread = new Thread(syncResultExchangeHandler);
            syncResultsExchangeHandlerThread.setName("SyncResultExchangeHandler-" + subExchangeId);
            syncResultsExchangeHandlerThread.start();

            logger.info("Awaiting for the other clients to complete fetching missing or outdated files...");

            // await here for completion of all clients
            try {
                // wait a maximum of 5000ms for each file
                long timeToWait = updatedPaths.size() * 5000L;

                if (0L == timeToWait) {
                    timeToWait = ANetworkHandler.MAX_WAITING_TIME;
                }

                syncResultExchangeHandler.await(timeToWait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for syncing of merged object store is complete on the other clients. Message: " + e.getMessage(), e);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(subExchangeId);

            if (! syncResultExchangeHandler.isCompleted()) {
                logger.error("SyncResultExchangeHandler should be completed after awaiting. Never starting file aggregators again until next sync...");
                return;
            }

            logger.info("Starting to send a sync complete exchange to restart event aggregation on all clients...");

            // start a sync complete exchange handler
            SyncCompleteExchangeHandler syncCompleteExchangeHandler = new SyncCompleteExchangeHandler(
                    this.client,
                    this.clientManager,
                    this.eventAggregator,
                    this.objectStore,
                    this.globalEventBus,
                    this.storageAdapter,
                    subExchangeId
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(subExchangeId, syncCompleteExchangeHandler);

            Thread syncCompleteExchangeHandlerThread = new Thread(syncCompleteExchangeHandler);
            syncCompleteExchangeHandlerThread.setName("SyncCompleteExchangeHandler-" + subExchangeId);
            syncCompleteExchangeHandlerThread.start();

            try {
                syncCompleteExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for the other clients to start their event aggregators. Message: " + e.getMessage(), e);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(subExchangeId);

            if (! syncCompleteExchangeHandler.isCompleted()) {
                logger.error("SyncCompleteExchangeHandler should be completed after awaiting. There might be clients which started their event aggregation again, but some didn't. Expect inconsistent results until next sync.");
                return;
            }

            logger.info("All event aggregators should be running again now");
            SyncCompleteExchangeHandlerResult completeExchangeHandlerResult = syncCompleteExchangeHandler.getResult();

            // un-flag the master is in progress indicator
            logger.debug("Setting master is in progress flag to false");
            this.client.getObjectDataReplyHandler().setMasterElected(false);

        } catch (Exception e) {
            logger.error("Got exception in ObjectStoreSyncer. Message: " + e.getMessage(), e);
        }
    }

}
