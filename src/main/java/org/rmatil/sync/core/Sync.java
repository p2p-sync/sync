package org.rmatil.sync.core;

import org.rmatil.sync.core.listener.SyncFolderChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.EventAggregator;
import org.rmatil.sync.event.aggregator.core.aggregator.HistoryMoveAggregator;
import org.rmatil.sync.event.aggregator.core.aggregator.IAggregator;
import org.rmatil.sync.event.aggregator.core.modifier.*;
import org.rmatil.sync.event.aggregator.core.pathwatcher.PerlockPathWatcherFactory;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.core.ObjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Sync {

    protected Path rootPath;

    protected IStorageAdapter storageAdapter;

    protected IEventAggregator eventAggregator;

    public Sync(Path rootPath)
            throws InputOutputException, IOException {
        this.rootPath = rootPath;

        this.initStorageAdapter();
        this.initEventAggregator();

        this.eventAggregator.start();
    }

    protected void initStorageAdapter() {
        this.storageAdapter = new LocalStorageAdapter(this.rootPath.resolve(".sync"));
    }

    protected void initEventAggregator()
            throws InputOutputException {
        ObjectStore objectStore = new ObjectStore(this.rootPath, "index.json", "object", this.storageAdapter);
        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(this.rootPath.relativize(rootPath.resolve(Paths.get(".sync"))));

        IEventListener eventListener = new SyncFolderChangeListener(objectStore);

        IModifier relativePathModifier = new RelativePathModifier(rootPath);
        IModifier addDirectoryContentModifier = new AddDirectoryContentModifier(this.rootPath, objectStore);
        IModifier ignorePathsModifier = new IgnorePathsModifier(ignoredPaths);
        IModifier ignoreDirectoryModifier = new IgnoreDirectoryModifier();

        IAggregator historyMoveAggregator = new HistoryMoveAggregator(objectStore.getObjectManager());

        this.eventAggregator = new EventAggregator(this.rootPath, new PerlockPathWatcherFactory());
        this.eventAggregator.setAggregationInterval(5000L);
        this.eventAggregator.addListener(eventListener);
        this.eventAggregator.addModifier(relativePathModifier);
        this.eventAggregator.addModifier(addDirectoryContentModifier);
        // TODO: check if ignoreDirectoryModifier can be used
        // TODO: Do we really want no modification of the path object of a directory?
//        this.eventAggregator.addModifier(ignoreDirectoryModifier);
        this.eventAggregator.addModifier(ignorePathsModifier);
        this.eventAggregator.addAggregator(historyMoveAggregator);
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

        try {
            Sync sync = new Sync(path);
        } catch (IOException | InputOutputException e) {
            e.printStackTrace();
        }
    }

}
