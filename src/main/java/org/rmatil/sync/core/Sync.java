package org.rmatil.sync.core;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.client.LocalStateObjectDataReplyHandler;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreFileChangeListener;
import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequest;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequest;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequest;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedRequest;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequest;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequestHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedRequest;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedRequestHandler;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.core.security.AccessManager;
import org.rmatil.sync.core.syncer.background.IBackgroundSyncer;
import org.rmatil.sync.core.syncer.background.NonBlockingBackgroundSyncer;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequest;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequestHandler;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.core.syncer.sharing.SharingSyncer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ConnectionConfiguration;
import org.rmatil.sync.network.core.Node;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The main application. Provides functionality to synchronise
 * files and folders among clients of multiple users.
 * Furthermore, it allows to share particular files with specific users.
 */
public class Sync {

    /**
     * The storage adapter managing the synced folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The file syncer used to propagate file change events to other clients
     */
    protected FileSyncer fileSyncer;

    /**
     * A syncer to allow sharing of files
     */
    protected SharingSyncer sharingSyncer;

    /**
     * A manager to allow changing of user / node related content
     */
    protected INodeManager nodeManager;

    /**
     * The node which back ups this application
     */
    protected INode node;

    /**
     * An event aggregator aggregating file system events
     */
    protected IEventAggregator eventAggregator;

    /**
     * The scheduled executor service for the background syncer
     */
    protected ScheduledExecutorService backgroundSyncerExecutorService;

    /**
     * The sync file change listener
     */
    protected SyncFileChangeListener syncFileChangeListener;

    /**
     * Initializes the app by means of creating
     * all required app folders and files. This includes
     * <p>
     * <ul>
     * <li>the ObjectStore folder</li>
     * <li>The ObjectStore's Object folder</li>
     * <li>read-write shared folder</li>
     * <li>read-only shared folder</li>
     * </ul>
     *
     * @param storageAdapter A tree storage adapter pointing to the root of the synchronised folder
     *
     * @throws InputOutputException If writing any of these elements fails
     */
    public static void init(ITreeStorageAdapter storageAdapter)
            throws InputOutputException {

        TreePathElement objectStoreFolder = new TreePathElement(Config.DEFAULT.getOsFolderName());

        if (! storageAdapter.exists(StorageType.DIRECTORY, objectStoreFolder)) {
            // create Object Store folder if not yet existing
            storageAdapter.persist(StorageType.DIRECTORY, objectStoreFolder, null);
        }

        TreePathElement objectStoreObjectFolder = new TreePathElement(
                Paths.get(objectStoreFolder.getPath()).resolve(Config.DEFAULT.getOsObjectFolderName()).toString()
        );

        if (! storageAdapter.exists(StorageType.DIRECTORY, objectStoreObjectFolder)) {
            // create the object folder inside the object store's directory
            storageAdapter.persist(StorageType.DIRECTORY, objectStoreObjectFolder, null);
        }

        TreePathElement sharedWithOthersReadWriteFolder = new TreePathElement(Config.DEFAULT.getSharedWithOthersReadWriteFolderName());

        if (! storageAdapter.exists(StorageType.DIRECTORY, sharedWithOthersReadWriteFolder)) {
            // create sharedWithOthers (read-write) folder
            storageAdapter.persist(StorageType.DIRECTORY, sharedWithOthersReadWriteFolder, null);
        }

        TreePathElement sharedWithOthersReadOnlyFolder = new TreePathElement(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName());

        if (! storageAdapter.exists(StorageType.DIRECTORY, sharedWithOthersReadOnlyFolder)) {
            storageAdapter.persist(StorageType.DIRECTORY, sharedWithOthersReadOnlyFolder, null);
        }

    }

    /**
     * Creates a new Sync application.
     *
     * @param treeStorageAdapter A storage adapter pointing to the root directory of the synchronised folder
     */
    public Sync(ITreeStorageAdapter treeStorageAdapter) {
        if (null == treeStorageAdapter) {
            throw new IllegalArgumentException("Storage Adapter must not be null");
        }

        this.storageAdapter = treeStorageAdapter;
    }

    /**
     * Start the client either as a bootstrap peer or connect it to an already online one
     * depending on the given application configuration.
     * If the remote
     *
     * @param applicationConfig The application configuration
     *
     * @return A client device representing the created and connected client
     *
     * @throws InitializationStartException If the client could not have been started
     */
    public ClientDevice connect(ApplicationConfig applicationConfig)
            throws InitializationStartException {
        IUser user = new User(
                applicationConfig.getUserName(),
                applicationConfig.getPassword(),
                applicationConfig.getSalt(),
                applicationConfig.getPublicKey(),
                applicationConfig.getPrivateKey(),
                new ArrayList<>()
        );

        UUID clientId = UUID.randomUUID();

        // Use feature driven configuration to have more control over the configuration details
        MBassador<IBusEvent> globalEventBus = new MBassador<>(
                new BusConfiguration()
                        .addFeature(Feature.SyncPubSub.Default())
                        .addFeature(Feature.AsynchronousHandlerInvocation.Default())
                        .addFeature(Feature.AsynchronousMessageDispatch.Default())
                        .addPublicationErrorHandler(new IPublicationErrorHandler.ConsoleLogger())
                        .setProperty(IBusConfiguration.Properties.BusId, "P2P-Sync-GlobalEventBus-" + UUID.randomUUID().toString())); // this is used for identification in #toString


        // Init object store
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(
                this.storageAdapter,
                Config.DEFAULT.getOsFolderName(),
                Config.DEFAULT.getOsIndexName(),
                Config.DEFAULT.getOsObjectFolderName()
        );


        IObjectStore objectStore = objectStoreInitializer.init();
        objectStoreInitializer.start();

        // Init client
        this.node = new Node(null, user, null);
        LocalStateObjectDataReplyHandler objectDataReplyHandler = new LocalStateObjectDataReplyHandler(
                this.storageAdapter,
                objectStore,
                this.node,
                globalEventBus,
                null,
                null,
                new AccessManager(objectStore)
        );

        // specify protocol
        objectDataReplyHandler.addRequestCallbackHandler(FileOfferRequest.class, FileOfferRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FilePushRequest.class, FilePushRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDeleteRequest.class, FileDeleteRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileMoveRequest.class, FileMoveRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FetchObjectStoreRequest.class, FetchObjectStoreRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDemandRequest.class, FileDemandRequestHandler.class);

        // file sharing
        objectDataReplyHandler.addRequestCallbackHandler(ShareRequest.class, ShareRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(SharedRequest.class, SharedRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(UnshareRequest.class, UnshareRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(UnsharedRequest.class, UnsharedRequestHandler.class);


        ClientInitializer clientInitializer = new ClientInitializer(
                new ConnectionConfiguration(
                        clientId.toString(),
                        applicationConfig.getPort(),
                        applicationConfig.getCacheTtl(),
                        applicationConfig.getPeerDiscoveryTimeout(),
                        applicationConfig.getPeerBootstrapTimeout(),
                        applicationConfig.getShutdownAnnounceTimeout(),
                        false
                ),
                objectDataReplyHandler,
                user,
                applicationConfig.getBootstrapLocation()
        );
        this.node = clientInitializer.init();
        clientInitializer.start();

        objectDataReplyHandler.setNode(this.node);

        this.nodeManager = clientInitializer.getNodeManager();

        objectDataReplyHandler.setNodeManager(this.nodeManager);

        this.fileSyncer = new FileSyncer(
                this.node.getUser(),
                this.node,
                this.nodeManager,
                this.storageAdapter,
                objectStore,
                globalEventBus
        );

        globalEventBus.subscribe(fileSyncer);

        // Add sync file change listener to event aggregator
        this.syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(syncFileChangeListener);

        globalEventBus.subscribe(syncFileChangeListener);

        IEventListener objectStoreFileChangeListener = new ObjectStoreFileChangeListener(objectStore);
        globalEventBus.subscribe(objectStoreFileChangeListener);

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(objectStoreFileChangeListener);
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(
                Paths.get(this.storageAdapter.getRootDir().getPath())
                        .relativize(Paths.get(this.storageAdapter.getRootDir().getPath()).resolve(Config.DEFAULT.getOsFolderName()))
        );

        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(
                this.storageAdapter,
                objectStore,
                eventListeners,
                ignoredPaths,
                applicationConfig.getIgnorePatterns(),
                5000L
        );
        this.eventAggregator = eventAggregatorInitializer.init();
        eventAggregatorInitializer.start();

        objectDataReplyHandler.setEventAggregator(this.eventAggregator);

        IBackgroundSyncer backgroundSyncer = new NonBlockingBackgroundSyncer(
                this.eventAggregator,
                this.node,
                this.nodeManager,
                objectStore,
                this.storageAdapter,
                globalEventBus,
                ignoredPaths,
                applicationConfig.getIgnorePatterns()
        );

        this.sharingSyncer = new SharingSyncer(
                this.node,
                this.nodeManager,
                this.storageAdapter,
                objectStore
        );

        // start the background syncer as first task, then reconcile every 5 minutes
        this.backgroundSyncerExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.backgroundSyncerExecutorService.scheduleAtFixedRate(backgroundSyncer, 0L, 300L, TimeUnit.SECONDS);

        // now set the peer address once we know it
        return new ClientDevice(
                this.node.getUser().getUserName(),
                this.node.getClientDeviceId(),
                this.node.getPeerAddress()
        );
    }

    /**
     * Stop all background processes and shut down this peer in the following order:
     * </p>
     * <ol>
     * <li>Background Syncer</li>
     * <li>Event Aggregator</li>
     * <li>Sync File Change Listener</li>
     * <li>Node</li>
     * </ol>
     */
    public void shutdown() {
        this.backgroundSyncerExecutorService.shutdown();
        this.eventAggregator.stop();
        this.syncFileChangeListener.shutdown();
        this.node.shutdown();
    }

    /**
     * Get the file syncer which is propagating file changes
     * to other nodes in the network.
     * <b>Note</b>: This method may return null before the node is connected
     *
     * @return The File Syncer or null, if this peer is not yet connected
     */
    public FileSyncer getFileSyncer() {
        return this.fileSyncer;
    }

    /**
     * Get the sharing syncer providing functionality to
     * share resp. unshare elements with other users.
     * <b>Note</b>: This method may return null before the node is connected
     *
     * @return The Sharing Syncer or null, if this peer is not yet connected
     */
    public SharingSyncer getSharingSyncer() {
        return this.sharingSyncer;
    }

    /**
     * Get the Event Aggregator, filtering resp. aggregating events from the storage layer.
     * <b>Note</b>: This method may return null before the node is connected
     *
     * @return The event aggregator or null, if this peer is not yet connected
     */
    public IEventAggregator getEventAggregator() {
        return this.eventAggregator;
    }

    /**
     * Get the executor service of the Background Syncer.
     * <b>Note</b>: This method may return null before the node is connected.
     *
     * @return The scheduled executor service of the Background Syncer or null, if this peer is not yet connected
     */
    public ScheduledExecutorService getBackgroundSyncerExecutorService() {
        return this.backgroundSyncerExecutorService;
    }

    /**
     * Get the storage adapter pointing to the synchronised folder
     *
     * @return The storage adapter pointing to the synchronised folder
     */
    public ITreeStorageAdapter getStorageAdapter() {
        return this.storageAdapter;
    }

    /**
     * Get the node of this sync instance.
     * <b>Note</b>: This method will return null, if this peer is not yet connected
     *
     * @return The node if connected. Null otherwise
     */
    public INode getNode() {
        return this.node;
    }
}
