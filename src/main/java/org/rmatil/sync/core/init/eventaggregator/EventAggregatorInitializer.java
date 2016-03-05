package org.rmatil.sync.core.init.eventaggregator;

import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
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

    Path                 rootPath;
    IObjectStore         objectStore;
    List<Path>           ignoredPaths;
    List<String>         ignoredPatterns;
    long                 aggregationInterval;
    IEventAggregator     eventAggregator;
    List<IEventListener> eventListeners;

    public EventAggregatorInitializer(Path rootPath, IObjectStore objectStore, List<IEventListener> eventListeners, List<Path> ignoredRelativePaths, List<String> ignoredPatterns, long aggregationInterval) {
        this.rootPath = rootPath;
        this.objectStore = objectStore;
        this.ignoredPaths = ignoredRelativePaths;
        this.ignoredPatterns = ignoredPatterns;
        this.eventListeners = eventListeners;
        this.aggregationInterval = aggregationInterval;
    }

    @Override
    public IEventAggregator init() {
        IModifier relativePathModifier = new RelativePathModifier(this.rootPath);
        IModifier addDirectoryContentModifier = new AddDirectoryContentModifier(this.rootPath, this.objectStore);
        IModifier ignorePathsModifier = new IgnorePathsModifier(ignoredPaths, this.ignoredPatterns);
        IModifier ignoreDirectoryModifier = new IgnoreDirectoryModifier(this.rootPath);
        IModifier sameHashModifier = new IgnoreSameHashModifier(this.objectStore.getObjectManager());

        IAggregator historyMoveAggregator = new HistoryMoveAggregator(objectStore.getObjectManager());

        this.eventAggregator = new EventAggregator(this.rootPath, new PerlockPathWatcherFactory());

        this.eventAggregator.setAggregationInterval(this.aggregationInterval);

        for (IEventListener listener : this.eventListeners) {
            this.eventAggregator.addListener(listener);
        }

        this.eventAggregator.addModifier(relativePathModifier);
        this.eventAggregator.addModifier(addDirectoryContentModifier);
        // we do not ignore directory modifications because we rely on the hash of the
        // directory contents for move events of directories
        // must be after addDirectoryContentModifier so that events for dir contents are generated
//        this.eventAggregator.addModifier(ignoreDirectoryModifier);
        this.eventAggregator.addModifier(ignorePathsModifier);
        this.eventAggregator.addModifier(sameHashModifier);

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
