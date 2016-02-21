package org.rmatil.sync.core;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.init.ApplicationConfig;
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
import org.rmatil.sync.core.model.RemoteClientLocation;
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
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    protected IStorageAdapter storageAdapter;

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
     * Creates a new Sync application.
     *
     * @param rootPath The path to the synced folder
     */
    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Creates the config file for the application
     * in the user's home directory
     *
     * @return Returns the path to the configuration directory
     *
     * @throws IOException If writing the file failed
     */
    public static Path createDefaultApplicationConfig()
            throws IOException {
        // replace any user home with the actual path to the folder
        String resolvedFolderPath = Config.DEFAULT.getConfigFolderPath().replaceFirst("^~", System.getProperty("user.home"));
        Path defaultFolderPath = Paths.get(resolvedFolderPath).toAbsolutePath();

        if (! defaultFolderPath.toFile().exists()) {
            Files.createDirectories(defaultFolderPath);
        }

        return Sync.createDefaultApplicationConfig(defaultFolderPath);
    }

    /**
     * Writes the default application config at the given path
     *
     * @param appConfigFolderPath The path to the application config directory
     *
     * @return The path on which the config was written
     *
     * @throws IOException If writing fails
     */
    public static Path createDefaultApplicationConfig(Path appConfigFolderPath)
            throws IOException {

        if (! appConfigFolderPath.toFile().exists()) {
            throw new FileNotFoundException(appConfigFolderPath.toString() + " (No such file or directory)");
        }

        Path configFilePath = appConfigFolderPath.resolve(Config.DEFAULT.getConfigFileName());
        if (! configFilePath.toFile().exists()) {
            Files.createFile(configFilePath);
        }

        // write the application config
        ApplicationConfig appConfig = new ApplicationConfig(
                null,
                null,
                null,
                0L,
                20000L,
                20000L,
                5000L,
                4003,
                appConfigFolderPath.resolve(Config.DEFAULT.getPublicKeyFileName()).toString(),
                appConfigFolderPath.resolve(Config.DEFAULT.getPrivateKeyFileName()).toString(),
                null
        );

        // actually write the config file
        Files.write(configFilePath, appConfig.toJson().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);


        return appConfigFolderPath;
    }

    /**
     * Returns the saved application config from the
     * user's home directory
     *
     * @return The application config
     *
     * @throws IOException              If the application config does not exist
     * @throws IllegalArgumentException If the application config file does not exist yet
     */
    public static ApplicationConfig getApplicationConfig()
            throws IllegalArgumentException, IOException {
        String resolvedFolderPath = Config.DEFAULT.getConfigFolderPath().replaceFirst("^~", System.getProperty("user.home"));
        Path defaultFolderPath = Paths.get(resolvedFolderPath).toAbsolutePath();

        return Sync.getApplicationConfig(defaultFolderPath);
    }

    /**
     * Returns the saved application config from the given path.
     *
     * @param appConfigFolderPath The path to the application config directory
     *
     * @return The saved application config
     *
     * @throws IOException              If the application config does not exist
     * @throws IllegalArgumentException If the application config file does not exist yet
     */
    public static ApplicationConfig getApplicationConfig(Path appConfigFolderPath) {
        Path configFilePath = appConfigFolderPath.resolve(Config.DEFAULT.getConfigFileName());

        if (! appConfigFolderPath.toFile().exists() || ! configFilePath.toFile().exists()) {
            throw new IllegalArgumentException("Application config does not exist yet. Create it first");
        }

        try {
            byte[] appConfigBytes = Files.readAllBytes(configFilePath);
            return ApplicationConfig.fromJson(new String(appConfigBytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read the application config. Try to rebuild it first. Error: " + e.getMessage());
        }
    }

    /**
     * Writes the given application config to the sync app
     * on the given path
     *
     * @param appConfig The app config to write
     *
     * @throws IOException If writing the application config failed
     */
    public static void writeApplicationConfig(ApplicationConfig appConfig)
            throws IOException {

        String resolvedFolderPath = Config.DEFAULT.getConfigFolderPath().replaceFirst("^~", System.getProperty("user.home"));
        Path defaultFolderPath = Paths.get(resolvedFolderPath).toAbsolutePath();

        Sync.writeApplicationConfig(appConfig, defaultFolderPath);
    }

    /**
     * Writes the given application config to the specified path.
     * Creates the path, if it does not exist yet
     *
     * @param appConfig           The application config to write
     * @param appConfigFolderPath The path to the config directory
     *
     * @throws IOException If writing the application config fails
     */
    public static void writeApplicationConfig(ApplicationConfig appConfig, Path appConfigFolderPath)
            throws IOException {
        if (! appConfigFolderPath.toFile().exists()) {
            Files.createDirectories(appConfigFolderPath);
        }

        Path configFilePath = appConfigFolderPath.resolve(Config.DEFAULT.getConfigFileName());
        if (! configFilePath.toFile().exists()) {
            Files.createFile(configFilePath);
        }

        String json = appConfig.toJson();
        Files.write(configFilePath, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

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
     * Start the client as bootstrap peer
     *
     * @param keyPair                 The RSA keypair which is used to sign & encrypt messages
     * @param userName                The username of the user
     * @param password                The password of the user
     * @param salt                    The salt of the user
     * @param cacheTtl                The time to live for values in the DHT cache
     * @param peerDiscoveryTimeout    The max timeout for peer discovery (in milliseconds)
     * @param peerBootstrapTimeout    The max timeout for bootstrapping to another peer (in milliseconds)
     * @param shutdownAnnounceTimeout The max timeout for announcing a friendly shutdown (in milliseconds)
     * @param port                    The port on which the client should be started
     *
     * @return A client device representing the created and bootstrapped client
     *
     * @throws InitializationStartException If the client could not have been started
     */
    public ClientDevice connect(KeyPair keyPair, String userName, String password, String salt, long cacheTtl, long peerDiscoveryTimeout, long peerBootstrapTimeout, long shutdownAnnounceTimeout, int port)
            throws InitializationStartException {
        return this.connect(keyPair, userName, password, salt, cacheTtl, peerDiscoveryTimeout, peerBootstrapTimeout, shutdownAnnounceTimeout, port, null);
    }

    /**
     * Start the client either as a bootstrap peer or connect it to an already online one.
     *
     * @param keyPair                 The RSA keypair which is used to sign & encrypt messages
     * @param userName                The username of the user
     * @param password                The password of the user
     * @param salt                    The salt of the user
     * @param cacheTtl                The time to live for values in the DHT cache
     * @param peerDiscoveryTimeout    The max timeout for peer discovery (in milliseconds)
     * @param peerBootstrapTimeout    The max timeout for bootstrapping to another peer (in milliseconds)
     * @param shutdownAnnounceTimeout The max timeout for announcing a friendly shutdown (in milliseconds)
     * @param port                    The port on which the client should be started
     * @param bootstrapLocation       The bootstrap location to which to connect. If null, then this peer will be created as bootstrap peer
     *
     * @return A client device representing the created and connected client
     *
     * @throws InitializationStartException If the client could not have been started
     */
    public ClientDevice connect(KeyPair keyPair, String userName, String password, String salt, long cacheTtl, long peerDiscoveryTimeout, long peerBootstrapTimeout, long shutdownAnnounceTimeout, int port, RemoteClientLocation bootstrapLocation)
            throws InitializationStartException {
        IUser user = new User(
                userName,
                password,
                salt,
                keyPair.getPublic(),
                keyPair.getPrivate(),
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
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(this.rootPath, ".sync", "index.json", "object");
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
                        port,
                        cacheTtl,
                        peerDiscoveryTimeout,
                        peerBootstrapTimeout,
                        shutdownAnnounceTimeout,
                        false
                ),
                objectDataReplyHandler,
                user,
                bootstrapLocation
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
        SyncFileChangeListener syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        // TODO: replace with non scheduled executor since it is a blocking queue
        executorService.scheduleAtFixedRate(syncFileChangeListener, 0, 10, TimeUnit.SECONDS);
        globalEventBus.subscribe(syncFileChangeListener);

        IEventListener objectStoreFileChangeListener = new ObjectStoreFileChangeListener(objectStore);
        globalEventBus.subscribe(objectStoreFileChangeListener);

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(objectStoreFileChangeListener);
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(Config.DEFAULT.getOsFolderName()))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, eventListeners, ignoredPaths, 5000L);
        this.eventAggregator = eventAggregatorInitializer.init();
        eventAggregatorInitializer.start();

        objectDataReplyHandler.setEventAggregator(this.eventAggregator);

        IBackgroundSyncer backgroundSyncer = new NonBlockingBackgroundSyncer(
                this.eventAggregator,
                this.node,
                this.nodeManager,
                objectStore,
                this.storageAdapter,
                globalEventBus
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
        return new ClientDevice(userName, clientId, node.getPeerAddress());
    }

    /**
     * Shuts down the application and the node backing it up
     */
    public void shutdown() {
        this.backgroundSyncerExecutorService.shutdown();
        this.eventAggregator.stop();
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

    public IStorageAdapter getStorageAdapter() {
        return storageAdapter;
    }
}
