package org.rmatil.sync.core;

import org.rmatil.sync.core.listener.SyncFolderChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.core.EventAggregator;
import org.rmatil.sync.event.aggregator.core.aggregator.HistoryMoveAggregator;
import org.rmatil.sync.event.aggregator.core.modifier.IgnorePathsModifier;
import org.rmatil.sync.event.aggregator.core.modifier.RelativePathModifier;
import org.rmatil.sync.event.aggregator.core.pathwatcher.PerlockPathWatcherFactory;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectManager;
import org.rmatil.sync.version.api.IVersionManager;
import org.rmatil.sync.version.core.ObjectManager;
import org.rmatil.sync.version.core.VersionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Sync {

    protected IStorageAdapter objectStorageManager;

    protected IObjectManager objectManager;

    protected IVersionManager versionManager;

    protected IEventAggregator eventAggregator;

    protected SyncFolderChangeListener syncFolderChangeListener;

    public Sync(Path rootPath)
            throws InputOutputException, IOException {
        this.objectStorageManager = new LocalStorageAdapter(rootPath.resolve(".sync"));
        this.objectManager = new ObjectManager("index.json", "object", this.objectStorageManager);
        this.versionManager = new VersionManager(this.objectManager);
        this.syncFolderChangeListener = new SyncFolderChangeListener(rootPath, this.versionManager);
        this.eventAggregator = new EventAggregator(rootPath, new PerlockPathWatcherFactory());
        this.eventAggregator.setAggregationInterval(5000L);
        this.eventAggregator.addListener(this.syncFolderChangeListener);

        List<Path> ignoredPaths = new ArrayList<>();
        ignoredPaths.add(rootPath.resolve(".sync"));
        IgnorePathsModifier ignorePathsModifier = new IgnorePathsModifier(ignoredPaths);

        RelativePathModifier relativePathModifier = new RelativePathModifier(rootPath);

        this.eventAggregator.addModifier(ignorePathsModifier);
        this.eventAggregator.addModifier(relativePathModifier);

        // add custom event aggregator which considers history
        HistoryMoveAggregator aggr = new HistoryMoveAggregator(this.objectManager);
        this.eventAggregator.addAggregator(aggr);

        this.eventAggregator.start();
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
