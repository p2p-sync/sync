package org.rmatil.sync.core.syncer.background.synccomplete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.IExtendedLocalStateRequestCallback;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Handles incoming {@link SyncCompleteRequest}s.
 * Starts the event aggregator again and calculates any differences
 * made in the mean time to the synchronized folder.
 *
 * @see SyncCompleteExchangeHandler
 */
public class SyncCompleteRequestHandler implements IExtendedLocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(SyncCompleteRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected IClientManager       clientManager;
    protected IEventAggregator     eventAggregator;
    protected SyncCompleteRequest  request;
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
        if (! (iRequest instanceof SyncCompleteRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + SyncCompleteRequest.class.getName());
        }

        this.request = (SyncCompleteRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Handling SyncCompleteRequest for exchangeId " + this.request.getExchangeId() + ", i.e. sending response back once we finished syncing the object store");

            // start event aggregator
            logger.info("Starting event aggregator on client (" + this.client.getPeerAddress().inetAddress().getHostName() + ":" + this.client.getPeerAddress().tcpPort() + ")");
            this.eventAggregator.start();

            // create a temporary second object store to get changes made in the mean time of syncing
            IStorageAdapter objectStoreStorageManager = this.objectStore.getObjectManager().getStorageAdapater();
            IPathElement pathElement = new LocalPathElement("changeObjectStore");
            if (objectStoreStorageManager.exists(StorageType.DIRECTORY, pathElement)) {
                objectStoreStorageManager.delete(pathElement);
            }

            objectStoreStorageManager.persist(StorageType.DIRECTORY,pathElement , null);

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
            HashMap<ObjectStore.MergedObjectType, Set<String>> updatedOrDeletedPaths =  this.objectStore.mergeObjectStore(changeObjectStore);

            // remove change object store again
            changeObjectStoreStorageManager.delete(new LocalPathElement("./"));

            Set<String> deletedPaths = updatedOrDeletedPaths.get(ObjectStore.MergedObjectType.DELETED);
            Set<String> updatedPaths = updatedOrDeletedPaths.get(ObjectStore.MergedObjectType.CHANGED);

            logger.info("Found " + deletedPaths.size() + " paths which have been deleted in the mean time of syncing");
            for (String deletedPath : deletedPaths) {
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

            logger.info("Found " + updatedPaths.size() + " paths which have changed");
            for (String updatedPath : updatedPaths) {
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


            IResponse syncCompleteResponse = new SyncCompleteResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress())
            );

            this.sendResponse(syncCompleteResponse);

        } catch (Exception e) {
            logger.error("Got exception in SyncCompleteRequestHandler. Message: " + e.getMessage(), e);
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
