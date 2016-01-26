package org.rmatil.sync.core.init.objecstore;

import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectStoreFileChangeListener implements IEventListener {

    final static Logger logger = LoggerFactory.getLogger(ObjectStoreFileChangeListener.class);

    protected IObjectStore objectStore;

    protected final Queue<IEvent> ignoredEvents;

    public ObjectStoreFileChangeListener(IObjectStore objectStore) {
        this.objectStore = objectStore;
        this.ignoredEvents = new ConcurrentLinkedQueue<>();
    }

    @Handler
    public void handleBusEvent(IgnoreBusEvent ignoreBusEvent) {
        logger.debug("Got notified from event bus: " + ignoreBusEvent.getEvent().getEventName() + " for file " + ignoreBusEvent.getEvent().getPath().toString());
        this.ignoredEvents.add(ignoreBusEvent.getEvent());
    }

    public void onChange(List<IEvent> list) {
        logger.trace("Got notified about " + list.size() + " new events");

        for (IEvent event : list) {

            synchronized (this.ignoredEvents) {
                for (IEvent eventToCheck : this.ignoredEvents) {
                    // weak ignoring events
                    if (eventToCheck.getEventName().equals(event.getEventName()) &&
                            eventToCheck.getPath().toString().equals(event.getPath().toString())) {

                        logger.info("Ignoring changing objectStore for event " + event.getEventName() + " for path " + event.getPath().toString());
                        this.ignoredEvents.remove(event);
                        return;
                    }
                }
            }

            switch (event.getEventName()) {
                case ModifyEvent.EVENT_NAME:
                    logger.trace("ModifyEvent for file " + event.getPath().toString());
                    try {
                        objectStore.onModifyFile(event.getPath().toString(), event.getHash());
                    } catch (InputOutputException e) {
                        logger.error("Failed to execute modifyEvent. Message: " + e.getMessage());
                    }
                    break;
                case CreateEvent.EVENT_NAME:
                    logger.trace("CreateEvent for file " + event.getPath().toString());
                    try {
                        objectStore.onCreateFile(event.getPath().toString(), event.getHash());
                    } catch (InputOutputException e) {
                        logger.error("Failed to execute CreateEvent. Message: " + e.getMessage());
                    }
                    break;
                case DeleteEvent.EVENT_NAME:
                    logger.trace("DeleteEvent for file " + event.getPath().toString());
                    try {
                        objectStore.onRemoveFile(event.getPath().toString());
                    } catch (InputOutputException e) {
                        logger.error("Failed to execute DeleteEvent. Message: " + e.getMessage());
                    }
                    break;
                case MoveEvent.EVENT_NAME:
                    logger.trace("MoveEvent for file " + event.getPath().toString());
                    try {
                        objectStore.onMoveFile(((MoveEvent) event).getPath().toString(), ((MoveEvent) event).getNewPath().toString());
                    } catch (InputOutputException e) {
                        logger.error("Failed to execute MoveEvent. Message: " + e.getMessage());
                    }
                    break;
                default:
                    logger.error("Failed to execute unknown event " + event.getClass().getName());
            }
        }
    }
}
