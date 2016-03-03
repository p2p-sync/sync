package org.rmatil.sync.test.syncer.file;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;

public class SyncFileChangeListenerTest {

    private static DummyFileSyncer        fileSyncer;
    private static SyncFileChangeListener syncFileChangeListener;

    @BeforeClass
    public static void setUp() {
        fileSyncer = new DummyFileSyncer();
        syncFileChangeListener = new SyncFileChangeListener(fileSyncer);
    }

    @Test
    public void test() {
        Thread listenerThread = new Thread(syncFileChangeListener);
        listenerThread.setName("TEST-syncFileChangeListener");
        listenerThread.start();

        IEvent testEvent = new CreateEvent(
                Paths.get("path/to/myFile.txt"),
                "myFile.txt",
                "someHash",
                System.currentTimeMillis()
        );

        IEvent testEvent2 = new ModifyEvent(
                Paths.get("path/to/myFile.txt"),
                "myFile.txt",
                "someHash",
                System.currentTimeMillis()
        );

        List<IEvent> events = new ArrayList<>();
        events.add(testEvent);

        CreateBusEvent createBusEvent = new CreateBusEvent(testEvent2);

        syncFileChangeListener.onChange(events);
        syncFileChangeListener.handleBusEvent(createBusEvent);

        // terminate
        syncFileChangeListener.shutdown();

        ConcurrentLinkedQueue<IEvent> queue = fileSyncer.getLinkedQueue();

        assertEquals("Queue should contain 2 events", 2, queue.size());
        assertEquals("First event should be createEvent", testEvent, queue.poll());
        assertEquals("2nd event should be modifyEvent", testEvent2, queue.poll());

    }
}
