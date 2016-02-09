package org.rmatil.sync.core;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationException;
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
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Sync {

    protected Path rootPath;

    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Creates the config file for the application
     *
     * @throws IOException If writing the file failed
     */
    public static void createDefaultApplicationConfig()
            throws IOException {
        // read the default config from the users config file
        Path defaultFolderPath = Paths.get(Config.DEFAULT.getConfigFolderPath()).toAbsolutePath();

        if (! defaultFolderPath.toFile().exists()) {
            Files.createDirectories(defaultFolderPath);
        }

        Path configFilePath = defaultFolderPath.resolve(Config.DEFAULT.getConfigFileName());
        if (! configFilePath.toFile().exists()) {
            Files.createFile(configFilePath);

            // write the application config
            ApplicationConfig appConfig = new ApplicationConfig(
                    null,
                    null,
                    null,
                    4003,
                    Config.DEFAULT.getPublicKeyFileName(),
                    Config.DEFAULT.getPrivateKeyFileName(),
                    null
            );

            // actually write the config file
            Files.write(defaultFolderPath, appConfig.toJson().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

        // generate and persist a keypair to disk
        Path publicKeyPath = defaultFolderPath.resolve(Config.DEFAULT.getPublicKeyFileName());
        Path privateKeyPath = defaultFolderPath.resolve(Config.DEFAULT.getPrivateKeyFileName());
        if (! publicKeyPath.toFile().exists() || ! privateKeyPath.toFile().exists()) {

            KeyPairGenerator keyPairGenerator;
            try {
                keyPairGenerator = KeyPairGenerator.getInstance("DSA");
            } catch (NoSuchAlgorithmException e) {
                throw new InitializationException(e);
            }

            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            Files.write(publicKeyPath, keyPair.getPublic().getEncoded(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.write(privateKeyPath, keyPair.getPrivate().getEncoded(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }

    }

    /**
     * Returns the saved application config
     *
     * @return The application config
     *
     * @throws IOException              If the application config does not exist
     * @throws IllegalArgumentException If the application config file does not exist yet
     */
    public static ApplicationConfig getApplicationConfig()
            throws IllegalArgumentException, IOException {
        Path defaultFolderPath = Paths.get(Config.DEFAULT.getConfigFolderPath()).toAbsolutePath();
        Path configFilePath = defaultFolderPath.resolve(Config.DEFAULT.getConfigFileName());

        if (! defaultFolderPath.toFile().exists() || ! configFilePath.toFile().exists()) {
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

        Path defaultFolderPath = Paths.get(Config.DEFAULT.getConfigFolderPath()).toAbsolutePath();

        if (! defaultFolderPath.toFile().exists()) {
            Files.createDirectories(defaultFolderPath);
        }

        Path configFilePath = defaultFolderPath.resolve(Config.DEFAULT.getConfigFileName());
        if (! configFilePath.toFile().exists()) {
            Files.createFile(configFilePath);

            String json = appConfig.toJson();
            Files.write(defaultFolderPath, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
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
     * Start the client either as a bootstrap peer or connect it to an already online one.
     *
     * @param keyPair           The RSA keypair which is used to sign & encrypt messages
     * @param userName          The username of the user
     * @param password          The password of the user
     * @param salt              The salt of the user
     * @param port              The port on which the client should be started
     * @param bootstrapLocation The bootstrap location to which to connect. If null, then this peer will be created as bootstrap peer
     *
     * @return A client device representing the created and bootstrapped client
     */
    public ClientDevice connect(KeyPair keyPair, String userName, String password, String salt, int port, RemoteClientLocation bootstrapLocation) {
        IUser user = new User(
                userName,
                password,
                salt,
                keyPair.getPublic(),
                keyPair.getPrivate(),
                new ArrayList<>()
        );

        UUID clientId = UUID.randomUUID();

        LocalStorageAdapter localStorageAdapter = new LocalStorageAdapter(rootPath);

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
        IClient client = new Client(null, user, null);
        LocalStateObjectDataReplyHandler objectDataReplyHandler = new LocalStateObjectDataReplyHandler(
                localStorageAdapter,
                objectStore,
                client,
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


        ClientInitializer clientInitializer = new ClientInitializer(objectDataReplyHandler, user, port, bootstrapLocation);
        client = clientInitializer.init();
        clientInitializer.start();

        // TODO: fix cycle with wrapper around client
        objectDataReplyHandler.setClient(client);

        IClientManager clientManager = clientInitializer.getClientManager();

        objectDataReplyHandler.setClientManager(clientManager);

        FileSyncer fileSyncer = new FileSyncer(
                client.getUser(),
                client,
                clientManager,
                new LocalStorageAdapter(rootPath),
                objectStore,
                globalEventBus
        );

        globalEventBus.subscribe(fileSyncer);

        // Add sync file change listener to event aggregator
        SyncFileChangeListener syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
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
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, eventListeners, ignoredPaths, 25000L);
        IEventAggregator eventAggregator = eventAggregatorInitializer.init();
        eventAggregatorInitializer.start();

        objectDataReplyHandler.setEventAggregator(eventAggregator);

        IBackgroundSyncer backgroundSyncer = new NonBlockingBackgroundSyncer(
                eventAggregator,
                client,
                clientManager,
                objectStore,
                localStorageAdapter,
                globalEventBus
        );

        // start the background syncer as first task, then reconcile every 10 minutes
        ScheduledExecutorService executorService1 = Executors.newSingleThreadScheduledExecutor();
        executorService1.scheduleAtFixedRate(backgroundSyncer, 0L, 600L, TimeUnit.SECONDS);

        // now set the peer address once we know it
        return new ClientDevice(userName, clientId, client.getPeerAddress());
    }
}
