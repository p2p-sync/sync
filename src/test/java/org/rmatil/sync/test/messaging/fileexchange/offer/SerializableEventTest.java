package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.SerializableEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class SerializableEventTest {

    protected static final String  EVENT_NAME  = MoveEvent.EVENT_NAME;
    protected static final Path    PATH        = Paths.get("path/to/myFile.txt");
    protected static final Path    NEW_PATH    = Paths.get("newPath/to/my/myFile.txt");
    protected static final long    TIMESTAMP   = 1234567890;
    protected static final Path    FILE_NAME   = PATH.getFileName();
    protected static final String  HASH        = "thisIsAHash";
    protected static final String  HASH_BEFORE = null;
    protected static final boolean IS_FILE     = true;

    protected static MoveEvent moveEvent;

    @BeforeClass
    public static void setUp() {
        moveEvent = new MoveEvent(
                PATH,
                NEW_PATH,
                FILE_NAME.toString(),
                HASH,
                TIMESTAMP
        );
    }

    @Test
    public void test() {
        SerializableEvent serializableEvent1 = new SerializableEvent(
                EVENT_NAME,
                PATH.toString(),
                NEW_PATH.toString(),
                TIMESTAMP,
                FILE_NAME.toString(),
                HASH,
                HASH_BEFORE,
                IS_FILE
        );

        SerializableEvent serializableEvent2 = SerializableEvent.fromEvent(moveEvent, HASH_BEFORE, IS_FILE);

        assertEquals("EventName is not equal", EVENT_NAME, serializableEvent1.getEventName());
        assertEquals("EventName is not equal", EVENT_NAME, serializableEvent2.getEventName());

        assertEquals("Path is not equal", PATH.toString(), serializableEvent1.getPath());
        assertEquals("Path is not equal", PATH.toString(), serializableEvent2.getPath());

        assertEquals("NewPath is not equal", NEW_PATH.toString(), serializableEvent1.getNewPath());
        assertEquals("NewPath is not equal", NEW_PATH.toString(), serializableEvent2.getNewPath());

        assertEquals("Timestamp is not equal", TIMESTAMP, serializableEvent1.getTimestamp());
        assertEquals("Timestamp is not equal", TIMESTAMP, serializableEvent2.getTimestamp());

        assertEquals("FileName is not equal", FILE_NAME.toString(), serializableEvent1.getFileName());
        assertEquals("FileName is not equal", FILE_NAME.toString(), serializableEvent2.getFileName());

        assertEquals("Hash is not equal", HASH, serializableEvent1.getHash());
        assertEquals("Hash is not equal", HASH, serializableEvent2.getHash());

        assertEquals("HashBefore is not equal", HASH_BEFORE, serializableEvent1.getHashBefore());
        assertEquals("HashBefore is not equal", HASH_BEFORE, serializableEvent2.getHashBefore());

        assertEquals("IsFile is not equal", IS_FILE, serializableEvent1.isFile());
        assertEquals("IsFile is not equal", IS_FILE, serializableEvent2.isFile());
    }
}
