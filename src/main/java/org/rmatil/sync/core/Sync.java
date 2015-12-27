package org.rmatil.sync.core;

import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.core.init.objectdatareply.FileDemandReplyInitializer;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Sync {

    protected Path rootPath;

    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    public void init() {
        // Init reply handlers
        String userName = "raphael";
        UUID clientId = UUID.randomUUID();
        ClientDevice clientDevice = new ClientDevice(userName, clientId, null);

        // Init object store
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(this.rootPath, ".sync", "index.json", "object");
        IObjectStore objectStore = objectStoreInitializer.init();

        FileDemandReplyInitializer fileDemandReplyInitializer = new FileDemandReplyInitializer(clientDevice, objectStore, this.rootPath, 1024);
        FileDemandRequestHandler fileDemandRequestHandler = fileDemandReplyInitializer.init();

        Map<Class, ObjectDataReply> replyHandlers = new HashMap<>();
        replyHandlers.put(FileDemandRequest.class, fileDemandRequestHandler);

        ClientInitializer clientInitializer = new ClientInitializer(replyHandlers, "raphael", "password", "salt", 4003);
        IClient client = clientInitializer.init();

        // Init event aggregator
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, ignoredPaths, 5000L);
        IEventAggregator eventAggregator = eventAggregatorInitializer.init();

        // sync folder
        clientInitializer.start();

        // now set the peer address once we know it
        clientDevice.setPeerAddress(client.getPeerAddress());

        objectStoreInitializer.start();
        eventAggregatorInitializer.start();
        fileDemandReplyInitializer.start();
    }

    public static void main(String[] args) {
        Path path = Paths.get("/tmp/sync-dir");
        Path syncDir = Paths.get("/tmp/sync-dir/.sync");

        try {
            if (! path.toFile().exists()) {
                Files.createDirectory(path);
            }
            if (! syncDir.toFile().exists()) {
                Files.createDirectory(syncDir);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Sync sync = new Sync(path);
        sync.init();
    }

}
