package org.rmatil.sync.core;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.bus.config.BusConfiguration;
import net.engio.mbassy.bus.config.Feature;
import net.engio.mbassy.bus.config.IBusConfiguration;
import net.engio.mbassy.bus.error.IPublicationErrorHandler;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.client.LocalStateObjectDataReplyHandler;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
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
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.core.syncer.background.BackgroundSyncer;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequest;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequestHandler;
import org.rmatil.sync.core.syncer.background.initsync.InitSyncRequest;
import org.rmatil.sync.core.syncer.background.initsync.InitSyncRequestHandler;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionRequest;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionRequestHandler;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteRequest;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteRequestHandler;
import org.rmatil.sync.core.syncer.background.syncresult.SyncResultRequest;
import org.rmatil.sync.core.syncer.background.syncresult.SyncResultRequestHandler;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.config.Config;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.core.dht.DhtStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

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

public class Sync {

    protected Path rootPath;

    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    public ClientDevice init(KeyPair keyPair, String userName, String password, String salt, int port, RemoteClientLocation bootstrapLocation) {
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
                null
        );

        // specify protocol
        objectDataReplyHandler.addRequestCallbackHandler(FileOfferRequest.class, FileOfferRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FilePushRequest.class, FilePushRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDeleteRequest.class, FileDeleteRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileMoveRequest.class, FileMoveRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(MasterElectionRequest.class, MasterElectionRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(InitSyncRequest.class, InitSyncRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FetchObjectStoreRequest.class, FetchObjectStoreRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(SyncResultRequest.class, SyncResultRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(FileDemandRequest.class, FileDemandRequestHandler.class);
        objectDataReplyHandler.addRequestCallbackHandler(SyncCompleteRequest.class, SyncCompleteRequestHandler.class);

        ClientInitializer clientInitializer = new ClientInitializer(objectDataReplyHandler, user, port, bootstrapLocation);
        client = clientInitializer.init();
        clientInitializer.start();

        // TODO: fix cycle with wrapper around client
        objectDataReplyHandler.setClient(client);

        DhtStorageAdapter dhtStorageAdapter = new DhtStorageAdapter(client.getPeerDht());

        IClientManager clientManager = new ClientManager(
                dhtStorageAdapter,
                Config.IPv4.getLocationsContentKey(),
                Config.IPv4.getPrivateKeyContentKey(),
                Config.IPv4.getPublicKeyContentKey(),
                Config.IPv4.getSaltContentKey(),
                Config.IPv4.getDomainKey()
        );

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

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, eventListeners, ignoredPaths, 25000L);
        IEventAggregator eventAggregator = eventAggregatorInitializer.init();
        eventAggregatorInitializer.start();

        objectDataReplyHandler.setEventAggregator(eventAggregator);

        BackgroundSyncer backgroundSyncer = new BackgroundSyncer(eventAggregator, client, clientManager);
        ScheduledExecutorService executorService1 = Executors.newSingleThreadScheduledExecutor();
        executorService1.scheduleAtFixedRate(backgroundSyncer, 30L, 600L, TimeUnit.SECONDS);

        // now set the peer address once we know it
        return new ClientDevice(userName, clientId, client.getPeerAddress());
    }

    public static void main(String[] args) {
        Path path = Paths.get("/tmp/sync-dir");
        Path syncDir = Paths.get("/tmp/sync-dir/.sync");

        Path path2 = Paths.get("/tmp/sync-dir2");
        Path syncDir2 = Paths.get("/tmp/sync-dir2/.sync");

        try {
            if (! path.toFile().exists()) {
                Files.createDirectory(path);
            }
            if (! syncDir.toFile().exists()) {
                Files.createDirectory(syncDir);
            }
            if (! path2.toFile().exists()) {
                Files.createDirectory(path2);
            }
            if (! syncDir2.toFile().exists()) {
                Files.createDirectory(syncDir2);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("DSA");
        } catch (NoSuchAlgorithmException e) {
            throw new InitializationException(e);
        }

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        Sync sync = new Sync(path);
        ClientDevice client1 = sync.init(keyPair, "raphael", "password", "salt", 4003, null);

        Sync sync2 = new Sync(path2);
        sync2.init(keyPair, "raphael", "password", "salt", 4004, new RemoteClientLocation(
                client1.getPeerAddress().inetAddress().getHostName(),
                client1.getPeerAddress().isIPv6(),
                client1.getPeerAddress().tcpPort()
        ));
    }

}
