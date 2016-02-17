package org.rmatil.sync.core.syncer.background;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.ConflictHandler;
import org.rmatil.sync.core.StringLengthComparator;
import org.rmatil.sync.core.Zip;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.api.PathType;
import org.rmatil.sync.version.core.ObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Starts a synchronization process only on the client running this instance.
 * All other clients will not be affected by the synchronization, removing the need
 * of master election as is done in the deprecated BlockingBackgroundSyncer.
 *
 * The procedure of syncing is as following:
 *
 * <ol>
 *     <li>Stop the event aggregation to safely update the object store manually</li>
 *     <li>Fetch the object stores from all clients</li>
 *     <li>Merge them on this client</li>
 *     <li>Download all missing or outdated files from the appropriate client</li>
 *     <li>Restart the event aggregation</li>
 *     <li>Sync merged object store with actual changes on disk</li>
 * </ol>
 *
 * {@inheritDoc}
 */
public class NonBlockingBackgroundSyncer implements IBackgroundSyncer {

    private static final Logger logger = LoggerFactory.getLogger(NonBlockingBackgroundSyncer.class);

    /**
     * The event aggregator of the client to stop
     */
    protected IEventAggregator eventAggregator;

    /**
     * The client to use to exchange messages
     */
    protected INode node;

    /**
     * The client manager to fetch client locations from
     */
    protected INodeManager nodeManager;

    /**
     * The object store of the synchronised folder
     */
    protected IObjectStore objectStore;

    /**
     * The storage adapter to access the synchronised folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The global event bus
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * @param eventAggregator The event aggregator to pause
     * @param node The client to exchange messages
     * @param nodeManager The client manager to fetch client locations from
     * @param objectStore The object store for the synchronised folder
     * @param storageAdapter The storage adapter of the synchronised folder
     * @param globalEventBus The global event bus to push events to
     */
    public NonBlockingBackgroundSyncer(IEventAggregator eventAggregator, INode node, INodeManager nodeManager, IObjectStore objectStore, IStorageAdapter storageAdapter, MBassador<IBusEvent> globalEventBus) {
        this.eventAggregator = eventAggregator;
        this.node = node;
        this.nodeManager = nodeManager;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void run() {
        try {
            UUID exchangeId = UUID.randomUUID();
            logger.info("Starting non blocking background syncer (Exchange: " + exchangeId + ")");
            this.eventAggregator.stop();

            FetchObjectStoreExchangeHandler fetchObjectStoreExchangeHandler = new FetchObjectStoreExchangeHandler(
                    this.node,
                    this.nodeManager,
                    exchangeId
            );

            this.node.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, fetchObjectStoreExchangeHandler);

            Thread fetchObjectStoreExchangeHandlerThread = new Thread(fetchObjectStoreExchangeHandler);
            fetchObjectStoreExchangeHandlerThread.setName("FetchObjectStoreExchangeHandler-" + exchangeId);
            fetchObjectStoreExchangeHandlerThread.start();

            logger.info("Waiting for fetching of object stores to complete");

            try {
                fetchObjectStoreExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for fetching all object stores");
            }

            this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

            if (! fetchObjectStoreExchangeHandler.isCompleted()) {
                logger.error("FetchObjectStoreExchangeHandler should be completed after awaiting. Since we do not know about the other clients object store, we abort background sync for exchange " + exchangeId);
                return;
            }

            FetchObjectStoreExchangeHandlerResult result = fetchObjectStoreExchangeHandler.getResult();

            Map<ClientDevice, IObjectStore> objectStores = Zip.unzipObjectStore(this.objectStore, result);

            // use a tree map for guaranteed ordering
            Map<String, ClientDevice> deletedPaths = new TreeMap<>(new StringLengthComparator());
            Map<String, ClientDevice> updatedPaths = new TreeMap<>(new StringLengthComparator());
            Map<String, ClientDevice> conflictPaths = new TreeMap<>(new StringLengthComparator());

            for (Map.Entry<ClientDevice, IObjectStore> entry : objectStores.entrySet()) {
                HashMap<ObjectStore.MergedObjectType, Set<String>> outdatedOrDeletedPaths = this.objectStore.mergeObjectStore(entry.getValue());

                for (String outDatedPath : outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CHANGED)) {
                    updatedPaths.put(outDatedPath, entry.getKey());
                }

                for (String deletedPath : outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.DELETED)) {
                    deletedPaths.put(deletedPath, entry.getKey());
                }

                for (String conflictPath : outdatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CONFLICT)) {
                    conflictPaths.put(conflictPath, entry.getKey());
                }

                entry.getValue().getObjectManager().getStorageAdapater().delete(new LocalPathElement("./"));
                this.objectStore.getObjectManager().getStorageAdapater().delete(new LocalPathElement(entry.getKey().getClientDeviceId().toString()));
            }

            // delete all removed files
            logger.info("Removing all (" + deletedPaths.size() + ") deleted files");
            for (Map.Entry<String, ClientDevice> entry : deletedPaths.entrySet()) {
                logger.debug("Removing deleted path " + entry.getKey());

                IPathElement elementToDelete = new LocalPathElement(entry.getKey());
                // only delete the file on disk if it actually exists
                if (this.storageAdapter.exists(StorageType.DIRECTORY, elementToDelete) || this.storageAdapter.exists(StorageType.FILE, elementToDelete)) {
                    this.storageAdapter.delete(elementToDelete);
                }
            }

            logger.info("Creating all (" + conflictPaths.size() + ") conflict files");
            for (Map.Entry<String, ClientDevice> entry: conflictPaths.entrySet()) {
                logger.debug("Creating conflict file " + entry.getKey());
                Path conflictFilePath = ConflictHandler.createConflictFile(
                        this.globalEventBus,
                        this.node.getClientDeviceId().toString(),
                        this.objectStore,
                        this.storageAdapter,
                        new LocalPathElement(entry.getKey())
                );



                // we have to emit an ignore event here for the file syncer
                // otherwise the final difference calculation will get an updated
                // path (i.e. actually an create event) and will try to sync it to the other clients
                if (null != conflictFilePath) {
                    this.globalEventBus.publish(new IgnoreBusEvent(
                            new ModifyEvent(
                                    conflictFilePath,
                                    conflictFilePath.getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));
                }

                // now add this to the updated paths, so that we can fetch the original again
                updatedPaths.put(entry.getKey(), entry.getValue());
            }

            // fetch all missing files
            logger.info("Fetching all (" + updatedPaths.size() + ") missing files");

            for (Map.Entry<String, ClientDevice> entry : updatedPaths.entrySet()) {
                UUID subExchangeId = UUID.randomUUID();
                logger.debug("Starting to fetch file " + entry.getKey() + " with subExchangeId " + subExchangeId + " (non blocking background sync " + exchangeId + ")");

                // before updating, check the actual content hash on disk
                // to prevent data loss during sync
                PathObject mergedPathObject = this.objectStore.getObjectManager().getObjectForPath(entry.getKey());
                Version lastVersion = mergedPathObject.getVersions().get(Math.max(0, mergedPathObject.getVersions().size() - 1));

                PathType pathType = mergedPathObject.getPathType();
                StorageType storageType = pathType.equals(PathType.DIRECTORY) ? StorageType.DIRECTORY : StorageType.FILE;

                // only check version, if the file does exist on our disk,
                // if not, we have to fetch it anyway
                if (this.storageAdapter.exists(storageType, new LocalPathElement(mergedPathObject.getAbsolutePath()))) {
                    this.objectStore.syncFile(this.storageAdapter.getRootDir().resolve(mergedPathObject.getAbsolutePath()).toFile());
                    PathObject modifiedPathObject = this.objectStore.getObjectManager().getObjectForPath(entry.getKey());
                    Version modifiedLastVersion = modifiedPathObject.getVersions().get(Math.max(0, modifiedPathObject.getVersions().size() - 1));

                    if (! modifiedLastVersion.equals(lastVersion)) {
                        // we just changed the file on this client while syncing...
                        // therefore we use this state and do not request an outdated state from another client
                        logger.info("Detected file change while merging object store (from other client or end user)... Using our state");
                        continue;
                    }
                }

                FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                        this.storageAdapter,
                        this.node,
                        this.nodeManager,
                        this.globalEventBus,
                        new NodeLocation(
                                entry.getValue().getClientDeviceId(),
                                entry.getValue().getPeerAddress()
                        ),
                        entry.getKey(),
                        subExchangeId
                );

                this.node.getObjectDataReplyHandler().addResponseCallbackHandler(subExchangeId, fileDemandExchangeHandler);

                Thread fileDemandExchangeHandlerThread = new Thread(fileDemandExchangeHandler);
                fileDemandExchangeHandlerThread.setName("FileDemandExchangeHandlerThread-" + subExchangeId);
                fileDemandExchangeHandlerThread.start();

                try {
                    fileDemandExchangeHandler.await();
                } catch (InterruptedException e) {
                    logger.error("Got interrupted while waiting for fileDemandExchangeHandler " + subExchangeId + " to complete. Message: " + e.getMessage());
                }

                this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(subExchangeId);

                if (! fileDemandExchangeHandler.isCompleted()) {
                    logger.error("FileDemandExchangeHandler " + subExchangeId + " should be completed after wait.");
                }
            }

            // start event aggregator
            logger.info("Starting event aggregator on client (" + this.node.getPeerAddress().inetAddress().getHostName() + ":" + this.node.getPeerAddress().tcpPort() + "): Non-blocking background sync " + exchangeId);
            this.eventAggregator.start();

            logger.info("Reconciling local disk changes with merged object store (non-blocking background sync " + exchangeId + ")");
            // create a temporary second object store to get changes made in the mean time of syncing
            IStorageAdapter objectStoreStorageManager = this.objectStore.getObjectManager().getStorageAdapater();
            IPathElement pathElement = new LocalPathElement("nonBlockingBackgroundSyncObjectStore");
            if (objectStoreStorageManager.exists(StorageType.DIRECTORY, pathElement)) {
                objectStoreStorageManager.delete(pathElement);
            }

            objectStoreStorageManager.persist(StorageType.DIRECTORY, pathElement, null);

            // create the temporary object store in the .sync folder
            Path rootPath = this.storageAdapter.getRootDir();
            IStorageAdapter changeObjectStoreStorageManager = new LocalStorageAdapter(objectStoreStorageManager.getRootDir().resolve(pathElement.getPath()));
            IObjectStore changeObjectStore = new ObjectStore(rootPath, "index.json", "object", changeObjectStoreStorageManager);

            // build object store for differences in the mean time
            List<String> ignoredPaths = new ArrayList<>();
            Path origSyncFolder = this.objectStore.getObjectManager().getStorageAdapater().getRootDir().getFileName();
            ignoredPaths.add(origSyncFolder.toString());
            changeObjectStore.sync(rootPath.toFile(), ignoredPaths);

            // get differences between disk and merged object store
            HashMap<ObjectStore.MergedObjectType, Set<String>> updatedOrDeletedPaths = this.objectStore.mergeObjectStore(changeObjectStore);

            // remove change object store again
            changeObjectStoreStorageManager.delete(new LocalPathElement("./"));

            Set<String> deletedPathsInTheMeanTime = updatedOrDeletedPaths.get(ObjectStore.MergedObjectType.DELETED);
            Set<String> updatedPathsInTheMeanTime = updatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CHANGED);

            logger.info("Found " + deletedPathsInTheMeanTime.size() + " paths which have been deleted in the mean time of syncing");
            for (String deletedPath : deletedPathsInTheMeanTime) {
                // publish a delete event to the SyncFileChangeListener
                logger.trace("Creating delete event for " + deletedPath);
                this.globalEventBus.publish(
                        new CreateBusEvent(
                                new DeleteEvent(
                                        Paths.get(deletedPath),
                                        Paths.get(deletedPath).getFileName().toString(),
                                        null,
                                        System.currentTimeMillis()
                                )
                        )
                );
            }

            logger.info("Found " + updatedPathsInTheMeanTime.size() + " paths which have changed in the mean time of syncing");
            for (String updatedPath : updatedPathsInTheMeanTime) {
                // publish modify events to SyncFileChangeListener
                logger.trace("Creating modify event for " + updatedPath);
                PathObject updatedPathObject = this.objectStore.getObjectManager().getObjectForPath(updatedPath);

                this.globalEventBus.publish(
                        new CreateBusEvent(
                                new ModifyEvent(
                                        Paths.get(updatedPath),
                                        Paths.get(updatedPath).getFileName().toString(),
                                        updatedPathObject.getVersions().get(Math.max(0, updatedPathObject.getVersions().size() - 1)).getHash(),
                                        System.currentTimeMillis()
                                )
                        )
                );
            }

            logger.info("Completed non-blocking background sync " + exchangeId);

        } catch (Exception e) {
            logger.error("Got exception in NonBlockingBackgroundSyncer. Message: " + e.getMessage(), e);
        }
    }
}
