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
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Files;
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
     * The root path of the synced folder
     */
    protected Path rootPath;

    /**
     * The storage adapter managing the synced folder
     */
    protected ITreeStorageAdapter storageAdapter;

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
     * Initializes the app in means of creating
     * all required app folders and files, e.g. the default application
     * configuration file, the object store directory and more.
     *
     * @throws IOException If writing any file or directory fails
     */
    public static void init(Path rootPath)
            throws IOException {

        if (! rootPath.toFile().exists()) {
            // create the synced folder
            Files.createDirectories(rootPath);
        }

        Path objectStoreFolder = rootPath.resolve(Config.DEFAULT.getOsFolderName());
        if (! objectStoreFolder.toFile().exists()) {
            Files.createDirectory(objectStoreFolder);
        }

        Path objectStoreObjectFolder = objectStoreFolder.resolve(Config.DEFAULT.getOsObjectFolderName());
        if (! objectStoreObjectFolder.toFile().exists()) {
            Files.createDirectories(objectStoreObjectFolder);
        }

        Path sharedWithOthersReadWriteFolder = rootPath.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName());
        if (! sharedWithOthersReadWriteFolder.toFile().exists()) {
            Files.createDirectory(sharedWithOthersReadWriteFolder);
        }

        Path sharedWithOthersReadOnlyFolder = rootPath.resolve(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName());
        if (! sharedWithOthersReadOnlyFolder.toFile().exists()) {
            Files.createDirectory(sharedWithOthersReadOnlyFolder);
        }
    }

    /**
     * Creates a new Sync application.
     *
     * @param rootPath The path to the synced folder
     */
    public Sync(Path rootPath) {
        if (null == rootPath) {
            throw new IllegalArgumentException("Root path must not be null");
        }

        this.rootPath = rootPath;
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

        this.storageAdapter = new LocalStorageAdapter(rootPath);

        // Use feature driven configuration to have more control over the configuration details
        MBassador<IBusEvent> globalEventBus = new MBassador<>(new BusConfiguration()
                .addFeature(Feature.SyncPubSub.Default())
                .addFeature(Feature.AsynchronousHandlerInvocation.Default())
                .addFeature(Feature.AsynchronousMessageDispatch.Default())
                .addPublicationErrorHandler(new IPublicationErrorHandler.ConsoleLogger())
                .setProperty(IBusConfiguration.Properties.BusId, "P2P-Sync-GlobalEventBus-" + UUID.randomUUID().toString())); // this is used for identification in #toString


        // Init object store
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(this.rootPath, Config.DEFAULT.getOsFolderName(), Config.DEFAULT.getOsIndexName(), Config.DEFAULT.getOsObjectFolderName());
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

        // TODO: fix cycle with wrapper around client
        objectDataReplyHandler.setNode(this.node);

        this.nodeManager = clientInitializer.getNodeManager();

        objectDataReplyHandler.setNodeManager(this.nodeManager);

        FileSyncer fileSyncer = new FileSyncer(
                this.node.getUser(),
                this.node,
                this.nodeManager,
                new LocalStorageAdapter(rootPath),
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
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(Config.DEFAULT.getOsFolderName()))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(
                this.rootPath,
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
     * Shuts down the application and the node backing it up
     */
    public void shutdown() {
        this.backgroundSyncerExecutorService.shutdown();
        this.eventAggregator.stop();
        this.syncFileChangeListener.shutdown();
        this.node.shutdown();
    }

    /**
     * Returns the root path
     *
     * @return
     */
    public Path getRootPath() {
        return rootPath;
    }

    public SharingSyncer getSharingSyncer() {
        return sharingSyncer;
    }

    public INodeManager getNodeManager() {
        return nodeManager;
    }

    public INode getNode() {
        return node;
    }

    public IEventAggregator getEventAggregator() {
        return eventAggregator;
    }

    public ScheduledExecutorService getBackgroundSyncerExecutorService() {
        return backgroundSyncerExecutorService;
    }

    public ITreeStorageAdapter getStorageAdapter() {
        return storageAdapter;
    }
}
