package org.rmatil.sync.core.init.objecstore;

import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ObjectStoreFileChangeListener implements IEventListener {

    final static Logger logger = LoggerFactory.getLogger(ObjectStoreFileChangeListener.class);

    protected IObjectStore objectStore;

    protected final Queue<IEvent> ignoredEvents;

    protected final Map<String, Set<Sharer>> sharerToAdd;

    public ObjectStoreFileChangeListener(IObjectStore objectStore) {
        this.objectStore = objectStore;
        this.ignoredEvents = new ConcurrentLinkedQueue<>();
        this.sharerToAdd = new ConcurrentHashMap<>();
    }

    @Handler
    public void handleBusEvent(IgnoreBusEvent ignoreBusEvent) {
        logger.debug("Got notified from event bus: " + ignoreBusEvent.getEvent().getEventName() + " for file " + ignoreBusEvent.getEvent().getPath().toString());
        this.ignoredEvents.add(ignoreBusEvent.getEvent());
    }

    @Handler
    public void handleSharerEvent(AddSharerToObjectStoreBusEvent addSharerEvent) {
        logger.debug("Got notified from event bus: AddSharerToObjectStoreBusEvent for file " + addSharerEvent.getRelativeFilePath() + " receiving " + addSharerEvent.getSharers().size() + " sharers");

        synchronized (this.sharerToAdd) {
            if (null == this.sharerToAdd.get(addSharerEvent.getRelativeFilePath())) {
                this.sharerToAdd.put(addSharerEvent.getRelativeFilePath(), new HashSet<>());
            }

            for (Sharer entry : addSharerEvent.getSharers()) {
                this.sharerToAdd.get(addSharerEvent.getRelativeFilePath()).add(entry);
            }
        }
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

            // add sharers to the file, if there should be any
            synchronized (this.sharerToAdd) {
                Set<Sharer> sharers = this.sharerToAdd.get(event.getPath().toString());
                if (null != sharers) {
                    try {
                        this.setSharers(event.getPath().toString(), sharers);
                    } catch (InputOutputException e) {
                        logger.error("Failed to write sharers for file " + event.getPath().toString());
                    }
                }
            }
        }
    }

    /**
     * Add sharers to the object store
     *
     * @param filePath The file path for which the sharers should be added
     * @param sharers  The sharers for the file
     *
     * @throws InputOutputException If accessing the object manager fails
     */
    public void setSharers(String filePath, Set<Sharer> sharers)
            throws InputOutputException {
        PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(filePath);

        if (null == pathObject) {
            logger.error("Could not add the sharer and the file id to path " + filePath + ". Aborting on this client and relying on the next background sync");
            return;
        }

        // generate the file id for the file
        UUID fileId = UUID.nameUUIDFromBytes(
                this.objectStore.getObjectManager().getHashForPath(filePath).getBytes()
        );

        pathObject.setFileId(fileId);

        if (! sharers.isEmpty()) {
            pathObject.setIsShared(true);
        }

        for (Sharer entry : sharers) {
            pathObject.getSharers().add(entry);
        }

        this.objectStore.getObjectManager().writeObject(pathObject);
    }
}
