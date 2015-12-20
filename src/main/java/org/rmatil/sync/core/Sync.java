package org.rmatil.sync.core;

import org.rmatil.sync.core.init.client.ClientInitializer;
import org.rmatil.sync.core.init.eventaggregator.EventAggregatorInitializer;
import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Sync {

    protected Path rootPath;

    public Sync(Path rootPath) {
        this.rootPath = rootPath;
    }

    public void init() {
        ObjectStoreInitializer objectStoreInitializer = new ObjectStoreInitializer(this.rootPath, ".sync", "index.json", "object");
        IObjectStore objectStore = objectStoreInitializer.init();

        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));
        EventAggregatorInitializer eventAggregatorInitializer = new EventAggregatorInitializer(this.rootPath, objectStore, ignoredPaths, 5000L);
        IEventAggregator eventAggregator = eventAggregatorInitializer.init();

        ClientInitializer clientInitializer = new ClientInitializer("raphael", "password", "salt", 4003);
        IClient client = clientInitializer.init();

        // TODO: add an event listener for change events on the filesystem
        // which sends messages to the other clients

        // sync folder
        objectStoreInitializer.start();
        eventAggregatorInitializer.start();
        clientInitializer.start();
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
