package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;

import java.io.Serializable;

/**
 * Is the serializable equivalent from a {@link IEvent} implementation.
 * This is needed in particular to send such events through the network,
 * since the path element of such an event is not serializable.
 */
public class SerializableEvent implements Serializable {

    private static final long serialVersionUID = - 80689716799205241L;
    
    /**
     * The name of the event.
     * Matching {@link IEvent#getEventName()}
     */
    protected String eventName;

    /**
     * The path of the event.
     * Matching the string value of {@link IEvent#getPath()}
     */
    protected String path;

    /**
     * Only set if the event is a {@link MoveEvent}.
     * Then the value should match the string representation of {@link MoveEvent#getNewPath()}
     */
    protected String newPath;

    /**
     * The timestamp of the event.
     * Matching {@link IEvent#getTimestamp()}
     */
    protected long timestamp;

    /**
     * The name of the file resp. directory.
     * Matching {@link IEvent#getName()}
     */
    protected String fileName;

    /**
     * The hash of the file which has changed.
     * Matching {@link IEvent#getHash()}
     */
    protected String hash;

    /**
     * The hash of the file before this event occurred.
     * No matching field in {@link IEvent}
     */
    protected String hashBefore;

    /**
     * Whether the path element affected by this event
     * is a file or not.
     * No matching field in {@link IEvent}
     */
    protected boolean isFile;

    /**
     * @param eventName  The name of the event. Matching {@link IEvent#getEventName()}
     * @param path       The path of the event. Matching the string value of {@link IEvent#getPath()}
     * @param newPath    Only set if the event is a {@link MoveEvent}. Then the value should match the string representation of {@link MoveEvent#getNewPath()}
     * @param timestamp  The timestamp of the event. Matching {@link IEvent#getTimestamp()}
     * @param fileName   The name of the file resp. directory. Matching {@link IEvent#getName()}
     * @param hash       The hash of the file which has changed. Matching {@link IEvent#getHash()}
     * @param hashBefore The hash of the file before this event occurred. No matching field in {@link IEvent}
     * @param isFile     Whether the path element affected by this event is a file or not.No matching field in {@link IEvent}
     */
    public SerializableEvent(String eventName, String path, String newPath, long timestamp, String fileName, String hash, String hashBefore, boolean isFile) {
        this.eventName = eventName;
        this.path = path;
        this.newPath = newPath;
        this.timestamp = timestamp;
        this.fileName = fileName;
        this.hash = hash;
        this.hashBefore = hashBefore;
        this.isFile = isFile;
    }

    /**
     * Creates a new {@link SerializableEvent} from the given event implementation
     * and the given additional parameters.
     *
     * @param event           The event from which should be converted into a serializable event
     * @param hashBeforeEvent The hash of the file before the given event occurred. May be null
     * @param isFile          Whether the path affected by the event is a file or not
     *
     * @return The created SerializableEvent
     */
    public static SerializableEvent fromEvent(IEvent event, String hashBeforeEvent, boolean isFile) {
        return new SerializableEvent(
                event.getEventName(),
                event.getPath().toString(),
                (event instanceof MoveEvent) ? ((MoveEvent) event).getNewPath().toString() : null,
                event.getTimestamp(),
                event.getName(),
                event.getHash(),
                hashBeforeEvent,
                isFile
        );
    }

    /**
     * The name of the event. Matching {@link IEvent#getEventName()}
     *
     * @return The event name
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * The path of the event. Matching the string value of {@link IEvent#getPath()}
     *
     * @return The path of the event
     */
    public String getPath() {
        return path;
    }

    /**
     * Only set if the event is a {@link MoveEvent}. Then the value should match the string representation of {@link MoveEvent#getNewPath()}
     *
     * @return The new path
     */
    public String getNewPath() {
        return newPath;
    }

    /**
     * The timestamp of the event. Matching {@link IEvent#getTimestamp()}
     *
     * @return The timestamp (milliseconds)
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * The name of the file resp. directory. Matching {@link IEvent#getName()}
     *
     * @return The path name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * The hash of the file which has changed. Matching {@link IEvent#getHash()}
     *
     * @return The hash of the file
     */
    public String getHash() {
        return hash;
    }

    /**
     * The hash of the file before this event occurred. No matching field in {@link IEvent}
     *
     * @return The hash of the file before this event occurred
     */
    public String getHashBefore() {
        return hashBefore;
    }

    /**
     * Whether the path element affected by this event is a file or not.No matching field in {@link IEvent}
     *
     * @return True, if the path represents a file, false otherwise
     */
    public boolean isFile() {
        return isFile;
    }
}
