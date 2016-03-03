package org.rmatil.sync.core.syncer.file;

import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.api.IFileSyncer;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A local filesystem event listener which triggers the synchronizing
 * of these events one-by-one using the file syncer.
 * <p>
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

    protected          IFileSyncer           fileSyncer;
    protected          BlockingQueue<IEvent> eventQueue;
    protected volatile boolean               isTerminated;

    /**
     * @param fileSyncer The file syncer propagate local file system events to other clients
     */
    public SyncFileChangeListener(IFileSyncer fileSyncer) {
        this.fileSyncer = fileSyncer;
        this.eventQueue = new LinkedBlockingQueue<>();
        this.isTerminated = false;
    }

    @Handler
    public void handleBusEvent(CreateBusEvent createBusEvent) {
        logger.debug("Got (" + this.toString() + ") notified from event bus (createBusEvent): " + createBusEvent.getEvent().getEventName() + " for file " + createBusEvent.getEvent().getPath().toString());
        this.eventQueue.add(createBusEvent.getEvent());
    }

    @Override
    public void onChange(List<IEvent> events) {
        this.eventQueue.addAll(events);
    }

    @Override
    public void run() {
        while (! isTerminated) {
            try {
                IEvent headEvent = this.eventQueue.take();

                // an event which has been caused due to handling a conflict
                this.fileSyncer.sync(headEvent);

            } catch (InterruptedException e) {
                logger.info("Got interrupted. Stopping to listen for file change events. FileSyncer will therefore not sync any change until this listener is restarted.");
                this.isTerminated = true;
            } catch (Exception e) {
                logger.error("Error in SyncFileChangeListener Thread. Message: " + e.getMessage(), e);
            }
        }
    }

    public void shutdown() {
        this.isTerminated = true;
        Thread.currentThread().interrupt();
    }

}
