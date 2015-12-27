package org.rmatil.sync.core.syncer.file;

import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A local filesystem event listener which triggers the synchronizing
 * of these events one-by-one using the file syncer.
 *
 * This listener must be invoked in a dedicated thread to ensure
 * that file system events can still be accepted (and blocking for the syncer to complete
 * resulting in lost file system events is avoided)
 * and handled even if the syncer is
 * not able to process them as fast as they occur.
 *
 * @see FileSyncer
 */
public class SyncFileChangeListener implements IEventListener, Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(SyncFileChangeListener.class);

    protected FileSyncer fileSyncer;
    protected Queue<IEvent> eventQueue;

    /**
     * @param fileSyncer The file syncer propagate local file system events to other clients
     */
    public SyncFileChangeListener(FileSyncer fileSyncer) {
        this.fileSyncer = fileSyncer;
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void onChange(List<IEvent> events) {
        this.eventQueue.addAll(events);
    }

    @Override
    public void run() {
        try {
            while (! this.eventQueue.isEmpty()) {
                IEvent headEvent = this.eventQueue.poll();

                // an event which has been caused due to handling a conflict
                this.fileSyncer.sync(headEvent);
            }

        } catch (Exception e) {
            logger.error("Error in SyncFileChangeListener Thread. Message: " + e.getMessage(), e);
        }
    }

}
