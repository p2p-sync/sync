package org.rmatil.sync.core;

import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.core.init.objectdatareply.FileDemandReplyInitializer;
import org.rmatil.sync.core.init.objectdatareply.FileOfferRequestReplyInitializer;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.core.syncer.file.FileSyncer;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.config.Config;
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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Sync {

    protected Path rootPath;

    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    public void init(KeyPair keyPair, String userName, String password, String salt, int port, RemoteClientLocation bootstrapLocation) {
        IUser user = new User(
                userName,
                password,
                salt,
                keyPair.getPublic(),
                keyPair.getPrivate(),
                new ArrayList<>()
        );

        // Init reply handlers
        UUID clientId = UUID.randomUUID();
        // TODO: fix init cycle!
        ClientDevice clientDevice = new ClientDevice(userName, clientId, null);

        // Init object store
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(this.rootPath, ".sync", "index.json", "object");
        IObjectStore objectStore = objectStoreInitializer.init();

        FileDemandReplyInitializer fileDemandReplyInitializer = new FileDemandReplyInitializer(clientDevice, objectStore, this.rootPath, 1024);
        FileDemandRequestHandler fileDemandRequestHandler = fileDemandReplyInitializer.init();

        // TODO: use one storage adapter for the synchronized folder -> for the whole project
        // TODO: use one storage adapter for the object store -> for the whole project
        LocalStorageAdapter localStorageAdapter = new LocalStorageAdapter(rootPath);

        FileOfferRequestReplyInitializer fileOfferRequestReplyInitializer = new FileOfferRequestReplyInitializer(clientDevice, objectStore, localStorageAdapter);
        FileOfferRequestHandler fileOfferRequestHandler = fileOfferRequestReplyInitializer.init();

        Map<Class, ObjectDataReply> replyHandlers = new HashMap<>();
        replyHandlers.put(FileDemandRequest.class, fileDemandRequestHandler);
        replyHandlers.put(FileOfferRequest.class, fileOfferRequestHandler);

        ClientInitializer clientInitializer = new ClientInitializer(replyHandlers, user, port, bootstrapLocation);
        IClient client = clientInitializer.init();

        // start client
        clientInitializer.start();

        DhtStorageAdapter dhtStorageAdapter = new DhtStorageAdapter(client.getPeerDht());

        FileSyncer fileSyncer = new FileSyncer(
                client.getUser(),
                client,
                new ClientManager(
                        dhtStorageAdapter,
                        Config.IPv4.getLocationsContentKey(),
                        Config.IPv4.getPrivateKeyContentKey(),
                        Config.IPv4.getPublicKeyContentKey(),
                        Config.IPv4.getDomainKey()
                ),
                new LocalStorageAdapter(rootPath),
                objectStore
        );

        SyncFileChangeListener syncFileChangeListener = new SyncFileChangeListener(fileSyncer);

        List<IEventListener> eventListeners = new ArrayList<>();
        eventListeners.add(syncFileChangeListener);

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, eventListeners, ignoredPaths, 5000L);
        IEventAggregator eventAggregator = eventAggregatorInitializer.init();

        // now set the peer address once we know it
        clientDevice.setPeerAddress(client.getPeerAddress());

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(syncFileChangeListener, 0, 10, TimeUnit.SECONDS);

        objectStoreInitializer.start();
        eventAggregatorInitializer.start();
        fileDemandReplyInitializer.start();
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
        sync.init(keyPair, "raphael", "password", "salt", 4003, null);

        Sync sync2 = new Sync(path2);
        sync2.init(keyPair, "raphael", "password", "salt", 4004, new RemoteClientLocation("192.168.1.34", false, 4003));
    }

}
