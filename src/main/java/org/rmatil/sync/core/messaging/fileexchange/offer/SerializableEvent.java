package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;

import java.io.Serializable;

public class SerializableEvent implements Serializable {

    protected String eventName;
    protected String path;
    protected String newPath;
    protected long   timestamp;
    protected String fileName;
    protected String hash;

    public SerializableEvent(String eventName, String path, String newPath, long timestamp, String fileName, String hash) {
        this.eventName = eventName;
        this.path = path;
        this.newPath = newPath;
        this.timestamp = timestamp;
        this.fileName = fileName;
        this.hash = hash;
    }

    public static SerializableEvent fromEvent(IEvent event) {
        return new SerializableEvent(
                event.getEventName(),
                event.getPath().toString(),
                (event instanceof MoveEvent) ? ((MoveEvent) event).getNewPath().toString() : null,
                event.getTimestamp(),
                event.getName(),
                event.getHash()
        );
    }

    public String getEventName() {
        return eventName;
    }

    public String getPath() {
        return path;
    }

    public String getNewPath() {
        return newPath;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHash() {
        return hash;
    }

}
