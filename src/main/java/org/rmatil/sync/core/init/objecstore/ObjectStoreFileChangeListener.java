package org.rmatil.sync.core.init.objecstore;

import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ObjectStoreFileChangeListener implements IEventListener {

    final static Logger logger = LoggerFactory.getLogger(ObjectStoreFileChangeListener.class);

    protected IObjectStore objectStore;

    public ObjectStoreFileChangeListener(IObjectStore objectStore) {
        this.objectStore = objectStore;

    }

    public void onChange(List<IEvent> list) {
        logger.trace("Got notified about " + list.size() + " new events");

        for (IEvent event : list) {
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
