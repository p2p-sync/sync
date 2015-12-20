package org.rmatil.sync.core.init.eventaggregator;

import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.init.objecstore.SyncFolderChangeListener;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.EventAggregator;
import org.rmatil.sync.event.aggregator.core.aggregator.HistoryMoveAggregator;
import org.rmatil.sync.event.aggregator.core.aggregator.IAggregator;
import org.rmatil.sync.event.aggregator.core.modifier.*;
import org.rmatil.sync.event.aggregator.core.pathwatcher.PerlockPathWatcherFactory;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class EventAggregatorInitializer implements IInitializer<IEventAggregator> {

    Path             rootPath;
    IObjectStore     objectStore;
    List<Path>       ignoredPaths;
    long             aggregationInterval;
    IEventAggregator eventAggregator;

    public EventAggregatorInitializer(Path rootPath, IObjectStore objectStore, List<Path> ignoredRelativePaths, long aggregationInterval) {
        this.rootPath = rootPath;
        this.objectStore = objectStore;
        this.ignoredPaths = ignoredRelativePaths;
        this.aggregationInterval = aggregationInterval;
    }

    @Override
    public IEventAggregator init() {
        IEventListener eventListener = new SyncFolderChangeListener(objectStore);

        IModifier relativePathModifier = new RelativePathModifier(this.rootPath);
        IModifier addDirectoryContentModifier = new AddDirectoryContentModifier(this.rootPath, this.objectStore);
        IModifier ignorePathsModifier = new IgnorePathsModifier(ignoredPaths);
        IModifier ignoreDirectoryModifier = new IgnoreDirectoryModifier();

        IAggregator historyMoveAggregator = new HistoryMoveAggregator(objectStore.getObjectManager());

        this.eventAggregator = new EventAggregator(this.rootPath, new PerlockPathWatcherFactory());

        this.eventAggregator.setAggregationInterval(this.aggregationInterval);

        this.eventAggregator.addListener(eventListener);

        this.eventAggregator.addModifier(relativePathModifier);
        this.eventAggregator.addModifier(addDirectoryContentModifier);
        // TODO: check if ignoreDirectoryModifier can be used
        // TODO: Do we really want no modification of the path object of a directory?
//        this.eventAggregator.addModifier(ignoreDirectoryModifier);
        this.eventAggregator.addModifier(ignorePathsModifier);

        this.eventAggregator.addAggregator(historyMoveAggregator);

        return this.eventAggregator;
    }


    @Override
    public void start()
            throws InitializationStartException {
        try {
            this.eventAggregator.start();
        } catch (IOException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop()
            throws InitializationStopException {
        this.eventAggregator.stop();
    }
}
