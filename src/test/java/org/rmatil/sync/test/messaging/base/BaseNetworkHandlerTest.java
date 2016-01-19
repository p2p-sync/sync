package org.rmatil.sync.test.messaging.base;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.client.LocalStateObjectDataReplyHandler;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequest;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequest;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequestHandler;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.dht.DhtStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.test.config.Config;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class BaseNetworkHandlerTest {

    protected static final Path   ROOT_TEST_DIR1     = Paths.get(Config.DEFAULT.getTestRootDir1());
    protected static final Path   ROOT_TEST_DIR2     = Paths.get(Config.DEFAULT.getTestRootDir2());
    protected static final int    PORT_CLIENT_1      = Config.DEFAULT.getPort1();
    protected static final int    PORT_CLIENT_2      = Config.DEFAULT.getPort2();
    protected static final String SYNC_FOLDER_NAME   = Config.DEFAULT.getSyncFolderName();
    protected static final String INDEX_FILE_NAME    = Config.DEFAULT.getIndexFileName();
    protected static final String OBJECT_FOLDER_NAME = Config.DEFAULT.getObjectFolderName();
    protected static final String USERNAME           = Config.DEFAULT.getUsername();
    protected static final String PASSWORD           = Config.DEFAULT.getPassword();
    protected static final String SALT               = Config.DEFAULT.getSalt();

    protected static KeyPair KEYPAIR;
    protected static IUser   USER;

    protected static final UUID CLIENT_ID_1 = UUID.randomUUID();
    protected static final UUID CLIENT_ID_2 = UUID.randomUUID();

    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_1;
    protected static MBassador<IBusEvent> GLOBAL_EVENT_BUS_2;

    protected static IStorageAdapter STORAGE_ADAPTER_1;
    protected static IObjectStore    OBJECT_STORE_1;

    protected static IStorageAdapter STORAGE_ADAPTER_2;
    protected static IObjectStore    OBJECT_STORE_2;

    protected static IClient CLIENT_1;
    protected static IClient CLIENT_2;

    protected static DhtStorageAdapter DHT_STORAGE_ADAPTER_1;
    protected static DhtStorageAdapter DHT_STORAGE_ADAPTER_2;

    protected static FileSyncer FILE_SYNCER_1;
    protected static FileSyncer FILE_SYNCER_2;

    protected static IEventAggregator EVENT_AGGREGATOR_1;
    protected static IEventAggregator EVENT_AGGREGATOR_2;

    protected static ClientDevice CLIENT_DEVICE_1;
    protected static ClientDevice CLIENT_DEVICE_2;

    protected static IClientManager CLIENT_MANAGER_1;
    protected static IClientManager CLIENT_MANAGER_2;

    @BeforeClass
    public static void setUp()
            throws IOException {

        createTestDirs();
        createObjectStoreDirs();

        KEYPAIR = createKeyPair();
        USER = createUser();

        GLOBAL_EVENT_BUS_1 = createGlobalEventBus();
        GLOBAL_EVENT_BUS_2 = createGlobalEventBus();

        STORAGE_ADAPTER_1 = new LocalStorageAdapter(ROOT_TEST_DIR1);
        STORAGE_ADAPTER_2 = new LocalStorageAdapter(ROOT_TEST_DIR2);


        OBJECT_STORE_1 = createObjectStore(ROOT_TEST_DIR1);
        OBJECT_STORE_2 = createObjectStore(ROOT_TEST_DIR2);

        CLIENT_1 = createClient(STORAGE_ADAPTER_1, OBJECT_STORE_1, GLOBAL_EVENT_BUS_1, PORT_CLIENT_1, null);
        CLIENT_2 = createClient(STORAGE_ADAPTER_2, OBJECT_STORE_2, GLOBAL_EVENT_BUS_2, PORT_CLIENT_2, new RemoteClientLocation(
                CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                CLIENT_1.getPeerAddress().isIPv6(),
                CLIENT_1.getPeerAddress().tcpPort()
        ));

        DHT_STORAGE_ADAPTER_1 = createDhtStorageAdapter(CLIENT_1);
        DHT_STORAGE_ADAPTER_2 = createDhtStorageAdapter(CLIENT_2);

        CLIENT_MANAGER_1 = createClientManager(DHT_STORAGE_ADAPTER_1);
        CLIENT_MANAGER_2 = createClientManager(DHT_STORAGE_ADAPTER_2);

        FILE_SYNCER_1 = createFileSyncer(CLIENT_1, DHT_STORAGE_ADAPTER_1, ROOT_TEST_DIR1, OBJECT_STORE_1, GLOBAL_EVENT_BUS_1);
        FILE_SYNCER_2 = createFileSyncer(CLIENT_2, DHT_STORAGE_ADAPTER_2, ROOT_TEST_DIR2, OBJECT_STORE_2, GLOBAL_EVENT_BUS_2);

        // Note: start the event aggregator manually in the subclasses
        EVENT_AGGREGATOR_1 = createEventAggregator(ROOT_TEST_DIR1, OBJECT_STORE_1, FILE_SYNCER_1, GLOBAL_EVENT_BUS_1);
        EVENT_AGGREGATOR_2 = createEventAggregator(ROOT_TEST_DIR2, OBJECT_STORE_2, FILE_SYNCER_2, GLOBAL_EVENT_BUS_2);

        CLIENT_DEVICE_1 = new ClientDevice(USERNAME, CLIENT_ID_1, CLIENT_1.getPeerAddress());
        CLIENT_DEVICE_2 = new ClientDevice(USERNAME, CLIENT_ID_2, CLIENT_2.getPeerAddress());
    }

    @AfterClass
    public static void tearDown() {
        EVENT_AGGREGATOR_1.stop();
        EVENT_AGGREGATOR_2.stop();

        CLIENT_2.shutdown();
        CLIENT_1.shutdown();

        deleteTestDirs();
    }


    /**
     * Creates the test directories
     *
     * @throws IOException If creating the directories failed
     */
    private static void createTestDirs()
            throws IOException {
        if (! ROOT_TEST_DIR1.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR1);
        }

        if (! ROOT_TEST_DIR2.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR2);
        }
    }

    /**
     * Creates the .sync folders
     *
     * @throws IOException If creating failed
     */
    private static void createObjectStoreDirs()
            throws IOException {
        Path syncFolder1 = ROOT_TEST_DIR1.resolve(SYNC_FOLDER_NAME);
        Path syncFolder2 = ROOT_TEST_DIR2.resolve(SYNC_FOLDER_NAME);

        if (! syncFolder1.toFile().exists()) {
            Files.createDirectory(syncFolder1);
        }

        if (! syncFolder2.toFile().exists()) {
            Files.createDirectory(syncFolder2);
        }
    }

    /**
     * Deletes the test directories and all contents in them
     */
    private static void deleteTestDirs() {
        if (ROOT_TEST_DIR1.toFile().exists()) {
            delete(ROOT_TEST_DIR1.toFile());
        }

        if (ROOT_TEST_DIR2.toFile().exists()) {
            delete(ROOT_TEST_DIR2.toFile());
        }
    }

    /**
     * Deletes recursively the given file (if it is a directory)
     * or just removes itself
     *
     * @param file The file or dir to remove
     *
     * @return True if the deletion was successful
     */
    public static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();

            if (null != contents) {
                for (File child : contents) {
                    delete(child);
                }
            }

            file.delete();

            return true;
        } else {
            return file.delete();
        }
    }

    /**
     * Generates the public private key pair
     *
     * @return The public private key pair
     */
    private static KeyPair createKeyPair() {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        } catch (NoSuchAlgorithmException e) {
            throw new InitializationException(e);
        }

        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Creates the global event bus to use
     *
     * @return The global event bus to use
     */
    private static MBassador<IBusEvent> createGlobalEventBus() {
        // Use feature driven configuration to have more control over the configuration details
        return new MBassador<>(new BusConfiguration()
                .addFeature(Feature.SyncPubSub.Default())
                .addFeature(Feature.AsynchronousHandlerInvocation.Default())
                .addFeature(Feature.AsynchronousMessageDispatch.Default())
                .addPublicationErrorHandler(new IPublicationErrorHandler.ConsoleLogger())
                .setProperty(IBusConfiguration.Properties.BusId, "P2P-Sync-TEST-GlobalEventBus-" + UUID.randomUUID().toString())); // this is used for identification in #toString
    }

    /**
     * Creates a new user
     *
     * @return The created user
     */
    private static IUser createUser() {
        return new User(
                USERNAME,
                PASSWORD,
                SALT,
                KEYPAIR.getPublic(),
                KEYPAIR.getPrivate(),
                new ArrayList<>()
        );
    }

    /**
     * Creates, inits and starts an object store in the given root test dir
     *
     * @param rootTestDir The root directory in which to create the object store
     *
     * @return The created object store
     */
    private static IObjectStore createObjectStore(Path rootTestDir) {
        ObjectStoreInitializer objectStoreInitializer1 = new ObjectStoreInitializer(
                rootTestDir,
                SYNC_FOLDER_NAME,
                INDEX_FILE_NAME,
                OBJECT_FOLDER_NAME
        );
        IObjectStore objectStore = objectStoreInitializer1.init();
        objectStoreInitializer1.start();

        return objectStore;
    }

    /**
     * Creates and starts a client with the provided configuration
     *
     * @param storageAdapter    The storage adapter to use for the local state object data reply handler
     * @param objectStore       The object store to access versions
     * @param globalEventBus    The global event bus for the client
     * @param port              The port of the client
     * @param bootstrapLocation The bootstrap location to use. May be null to start as bootstrap peer
     *
     * @return The configured and started client
     */
    private static IClient createClient(IStorageAdapter storageAdapter, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, int port, RemoteClientLocation bootstrapLocation) {
        IClient client = new Client(null, USER, null);
        LocalStateObjectDataReplyHandler objectDataReplyHandler = new LocalStateObjectDataReplyHandler(
                storageAdapter,
                objectStore,
                client,
                globalEventBus,
                null,
                null
        );
        // specify protocol
        objectDataReplyHandler.addRequestCallbackHandler(FileOfferRequest.class, FileOfferRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FilePushRequest.class, FilePushRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDeleteRequest.class, FileDeleteRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileMoveRequest.class, FileMoveRequestHandler.class);

        ClientInitializer clientInitializer = new ClientInitializer(objectDataReplyHandler, USER, port, bootstrapLocation);
        client = clientInitializer.init();
        clientInitializer.start();

        objectDataReplyHandler.setClient(client);

        return client;
    }

    /**
     * Creates a new DHT storage adapter using the given client
     *
     * @param client The client of which the peerDHT is used to create a DHTStorageAdapter
     *
     * @return The created storage adapter
     */
    private static DhtStorageAdapter createDhtStorageAdapter(IClient client) {
        return new DhtStorageAdapter(client.getPeerDht());
    }

    /**
     * Creates a new file syncer
     *
     * @param client            The client to use for sending messages
     * @param dhtStorageAdapter The storage adapter to access client locations
     * @param rootPath          The root path of the synchronized folder
     * @param objectStore       The object store to access versions
     * @param globalEventBus    The global event bus to publish events to
     *
     * @return The created file syncer
     */
    private static FileSyncer createFileSyncer(IClient client, DhtStorageAdapter dhtStorageAdapter, Path rootPath, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus) {
        FileSyncer fileSyncer = new FileSyncer(
                client.getUser(),
                client,
                new ClientManager(
                        dhtStorageAdapter,
                        org.rmatil.sync.network.config.Config.IPv4.getLocationsContentKey(),
                        org.rmatil.sync.network.config.Config.IPv4.getPrivateKeyContentKey(),
                        org.rmatil.sync.network.config.Config.IPv4.getPublicKeyContentKey(),
                        org.rmatil.sync.network.config.Config.IPv4.getSaltContentKey(),
                        org.rmatil.sync.network.config.Config.IPv4.getDomainKey()
                ),
                new LocalStorageAdapter(rootPath),
                objectStore,
                globalEventBus
        );

        globalEventBus.subscribe(fileSyncer);

        return fileSyncer;
    }

    /**
     * Create and start event aggregator
     *
     * @param rootPath       The root path to watch for changes
     * @param objectStore    The object store to check for versions
     * @param fileSyncer     The file syncer to create the listener for events
     * @param globalEventBus The global event bus to add events
     *
     * @return The started event aggregator
     */
    private static IEventAggregator createEventAggregator(Path rootPath, IObjectStore objectStore, FileSyncer fileSyncer, MBassador<IBusEvent> globalEventBus) {
        // Add sync file change listener to event aggregator
        SyncFileChangeListener syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

        // the listener fetches all events every 10 seconds to propagate to other clients
        executorService.scheduleAtFixedRate(syncFileChangeListener, 0, 10, TimeUnit.SECONDS);

        globalEventBus.subscribe(syncFileChangeListener);

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(
                rootPath,
                objectStore,
                eventListeners,
                ignoredPaths,
                25000L
        );

        return eventAggregatorInitializer.init();
    }

    /**
     * Create a client manager
     *
     * @param dhtStorageAdapter The dht storage adapter where the client locations are saved
     *
     * @return The client manager
     */
    private static IClientManager createClientManager(DhtStorageAdapter dhtStorageAdapter) {
        return new ClientManager(
                dhtStorageAdapter,
                org.rmatil.sync.network.config.Config.IPv4.getLocationsContentKey(),
                org.rmatil.sync.network.config.Config.IPv4.getPrivateKeyContentKey(),
                org.rmatil.sync.network.config.Config.IPv4.getPublicKeyContentKey(),
                org.rmatil.sync.network.config.Config.IPv4.getSaltContentKey(),
                org.rmatil.sync.network.config.Config.IPv4.getDomainKey()
        );
    }
}
