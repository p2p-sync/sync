package org.rmatil.sync.test.eventbus;

import org.junit.Test;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class CreateBusEventTest {

    @Test
    public void testEvent() {
        Path path = Paths.get("somePath/to/testFile.txt");

        IEvent createEvent = new CreateEvent(
                path,
                path.getFileName().toString(),
                "someHash",
                System.currentTimeMillis()
        );

        CreateBusEvent createBusEvent = new CreateBusEvent(createEvent);

        assertEquals("Event is not the same", createEvent, createBusEvent.getEvent());
    }

}
