package org.rmatil.sync.core.syncer.background.synccomplete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.syncer.background.BlockingBackgroundSyncer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Sends a notification to all clients causing them to
 * restart their event aggregators and propagate any changes
 * made in the mean time to all clients.
 *
 * @see BlockingBackgroundSyncer
 *
 * @deprecated As of 0.1. Will be removed in future releases.
 */
public class SyncCompleteExchangeHandler extends ANetworkHandler<SyncCompleteExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SyncCompleteExchangeHandler.class);

    /**
     * The client manager to fetch all client locations
     */
    protected IClientManager clientManager;

    /**
     * The event aggregator to restart on the master client
     */
    protected IEventAggregator eventAggregator;

    /**
     * The object store on the master client
     */
    protected IObjectStore objectStore;

    /**
     * The global event bus of the master client
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The storage adapter on the master client
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The id for this exchange
     */
    protected UUID exchangeId;

    /**
     * @param client          The client to send messages
     * @param clientManager   The client manager of the master client to fetch all client locations from
     * @param eventAggregator The event aggregator on the master client
     * @param objectStore     The object store on the master client
     * @param globalEventBus  The global event bus on the master client
     * @param exchangeId      The id for this exchange
     */
    public SyncCompleteExchangeHandler(IClient client, IClientManager clientManager, IEventAggregator eventAggregator, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, IStorageAdapter storageAdapter, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.eventAggregator = eventAggregator;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;
        this.storageAdapter = storageAdapter;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            logger.info("Notifying all clients about completion of synchronization");
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            IRequest syncCompleteRequest = new SyncCompleteRequest(
                    this.exchangeId,
                    new ClientDevice(super.client.getUser().getUserName(), super.client.getClientDeviceId(), super.client.getPeerAddress()),
                    clientLocations
            );

            super.sendRequest(syncCompleteRequest);

            this.restartEventAggregationAndSyncChanges();

        } catch (Exception e) {
            logger.error("Got exception in SyncCompleteExchangeHandler. Message: " + e.getMessage());
        }
    }

    @Override
    public SyncCompleteExchangeHandlerResult getResult() {
        return new SyncCompleteExchangeHandlerResult();
    }


    /**
     * Computes the changes made between reconciliation is done.
     * Additionally, starts the event aggregator again
     *
     * @throws IOException          If starting the event aggregator fails
     * @throws InputOutputException If accessing the object store fails
     */
    protected void restartEventAggregationAndSyncChanges()
            throws IOException, InputOutputException {
        // start event aggregator
        logger.info("Starting event aggregator on master client (" + this.client.getPeerAddress().inetAddress().getHostName() + ":" + this.client.getPeerAddress().tcpPort() + ")");
        this.eventAggregator.start();

        // create a temporary second object store to get changes made in the mean time of syncing
        IStorageAdapter objectStoreStorageManager = this.objectStore.getObjectManager().getStorageAdapater();

        IPathElement pathElement = new LocalPathElement("changeObjectStore");
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
    }
}
