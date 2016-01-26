package org.rmatil.sync.core.syncer.sharing;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.api.ISharingSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SyncSharingChangeListener implements Runnable {

    protected static final Logger logger = LoggerFactory.getLogger(SyncSharingChangeListener.class);

    protected ISharingSyncer     sharingSyncer;
    protected Queue<IShareEvent> eventQueue;

    /**
     * @param sharingSyncer The sharing syncer which propagate share events to own clients as well as other user's clients
     */
    public SyncSharingChangeListener(ISharingSyncer sharingSyncer) {
        this.sharingSyncer = sharingSyncer;
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }

    public void onChange(List<IShareEvent> events) {
        this.eventQueue.addAll(events);
    }

    @Override
    public void run() {
        try {
            while (! this.eventQueue.isEmpty()) {
                IShareEvent headEvent = this.eventQueue.poll();

                // an event which has been caused due to handling a conflict
                this.sharingSyncer.sync(headEvent);
            }

        } catch (Exception e) {
            logger.error("Error in SyncSharingChangeListener Thread. Message: " + e.getMessage(), e);
        }
    }
}
