package org.rmatil.sync.core.init.objecstore;

import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.eventbus.AddOwnerAndAccessTypeToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.CleanModifyOsIgnoreEventsBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreObjectStoreUpdateBusEvent;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
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

    protected final Map<String, AddOwnerAndAccessTypeToObjectStoreBusEvent> ownersToAdd;

    public ObjectStoreFileChangeListener(IObjectStore objectStore) {
        this.objectStore = objectStore;
        this.ignoredEvents = new ConcurrentLinkedQueue<>();
        this.sharerToAdd = new ConcurrentHashMap<>();
        this.ownersToAdd = new ConcurrentHashMap<>();
    }

    @Handler
    public void handleBusEvent(IgnoreObjectStoreUpdateBusEvent ignoreBusEvent) {
        logger.debug("Got notified from event bus: IgnoreObjectStoreUpdateBusEvent " + ignoreBusEvent.getEvent().getEventName() + " for file " + ignoreBusEvent.getEvent().getPath().toString());
        this.ignoredEvents.add(ignoreBusEvent.getEvent());
    }

    @Handler
    public void handleCleanOsIgnoreEvents(CleanModifyOsIgnoreEventsBusEvent event) {
        logger.debug("Got clean up ignore events event from global event bus for file " + event.getRelativePath());
        synchronized (this.ignoredEvents) {
            Iterator<IEvent> itr = this.ignoredEvents.iterator();
            while (itr.hasNext()) {
                IEvent ev = itr.next();
                if (ev.getPath().toString().equals(event.getRelativePath()) &&
                        ev instanceof ModifyEvent) {
                    logger.trace("Remove modify event " + ev.getEventName());
                    itr.remove();
                }
            }
        }
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

    @Handler
    public void handleOwnerEvent(AddOwnerAndAccessTypeToObjectStoreBusEvent event) {
        logger.debug("Got notified from event bus: AddOwnerAndAccessTypeToObjectStoreBusEvent for file " + event.getRelativeFilePath());

        synchronized (this.ownersToAdd) {
            this.ownersToAdd.put(event.getRelativeFilePath(), event);
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
                        objectStore.onMoveFile(event.getPath().toString(), ((MoveEvent) event).getNewPath().toString());
                    } catch (InputOutputException e) {
                        logger.error("Failed to execute MoveEvent. Message: " + e.getMessage());
                    }
                    break;
                default:
                    logger.error("Failed to execute unknown event " + event.getClass().getName());
            }

            // add sharers to the file, if there should be any
            synchronized (this.sharerToAdd) {
                logger.trace("Looking for sharers to add to file " + event.getPath().toString());

                Set<Sharer> sharers = this.sharerToAdd.get(event.getPath().toString());
                // set the sharers only if not a delete event, since all sharers should be removed then
                if (null != sharers && ! sharers.isEmpty() && ! (event instanceof DeleteEvent)) {
                    logger.trace("Adding " + sharers.size() + " sharers to file " + event.getPath().toString());

                    try {
                        this.setSharers(event.getPath().toString(), sharers);
                        // remove all sharers
                        this.sharerToAdd.get(event.getPath().toString()).clear();
                    } catch (InputOutputException e) {
                        logger.error("Failed to write sharers for file " + event.getPath().toString() + ". Message: " + e.getMessage(), e);
                    }
                } else if (null != sharers && ! sharers.isEmpty() && (event instanceof DeleteEvent)) {
                    logger.trace("Removing sharers to add due to a delete event for file " + event.getPath().toString());
                    // remove all sharers
                    this.sharerToAdd.get(event.getPath().toString()).clear();
                }
            }

            synchronized (this.ownersToAdd) {
                logger.trace("Looking for owner and access type to add to file " + event.getPath().toString());

                AddOwnerAndAccessTypeToObjectStoreBusEvent addOwnerAndAccessTypeToObjectStoreBusEvent = this.ownersToAdd.get(event.getPath().toString());
                if (null != addOwnerAndAccessTypeToObjectStoreBusEvent && ! (event instanceof DeleteEvent)) {
                    logger.trace("Adding owner and access type to file " + event.getPath().toString());
                    try {
                        this.setOwnerAndAccessType(
                                addOwnerAndAccessTypeToObjectStoreBusEvent.getRelativeFilePath(),
                                addOwnerAndAccessTypeToObjectStoreBusEvent.getOwner(),
                                addOwnerAndAccessTypeToObjectStoreBusEvent.getAccessType()
                        );

                        // remove entry
                        this.ownersToAdd.remove(event.getPath().toString());
                    } catch (InputOutputException e) {
                        logger.error("Failed to write owner and access type for file " + event.getPath().toString() + ". Message: " + e.getMessage(), e);
                    }
                } else if (null != addOwnerAndAccessTypeToObjectStoreBusEvent && (event instanceof DeleteEvent)) {
                    logger.trace("Removing owner and access type to add due to a delete event for file " + event.getPath().toString());
                    this.ownersToAdd.remove(event.getPath().toString());
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

        if (! sharers.isEmpty()) {
            pathObject.setIsShared(true);
        }

        for (Sharer entry : sharers) {
            pathObject.getSharers().add(entry);
        }

        this.objectStore.getObjectManager().writeObject(pathObject);
    }

    public void setOwnerAndAccessType(String filePath, String owner, AccessType accessType)
            throws InputOutputException {
        PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(filePath);

        if (null == pathObject) {
            logger.error("Could not add owner and access type to the file on path " + filePath + ". Aborting on this client and relying on the next background sync");
            return;
        }

        pathObject.setOwner(owner);
        pathObject.setAccessType(accessType);

        this.objectStore.getObjectManager().writeObject(pathObject);
    }
}
