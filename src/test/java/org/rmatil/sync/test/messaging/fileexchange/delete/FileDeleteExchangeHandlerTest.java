package org.rmatil.sync.test.messaging.fileexchange.delete;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;

public class FileDeleteExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static Path TEST_FILE_1 = TEST_DIR_1.resolve("testFile.txt");
    protected static UUID EXCHANGE_ID = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());
    }

    @Test
    public void testDelete()
            throws InputOutputException, InterruptedException {
        STORAGE_ADAPTER_1.delete(new LocalPathElement(TEST_DIR_1.toString()));

        // resync after deletion
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        DeleteEvent deleteEvent = new DeleteEvent(
                TEST_DIR_1,
                TEST_DIR_1.getFileName().toString(),
                "noNeedToKnowTheHashCurrently",
                System.currentTimeMillis()
        );

        FileDeleteExchangeHandler fileDeleteExchangeHandler = new FileDeleteExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                GLOBAL_EVENT_BUS_1,
                CLIENT_LOCATIONS,
                deleteEvent
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileDeleteExchangeHandler);

        Thread fileDeleteExchangeHandlerThread = new Thread(fileDeleteExchangeHandler);
        fileDeleteExchangeHandlerThread.setName("TEST-FileDeleteExchangeHandler");
        fileDeleteExchangeHandlerThread.start();

        // wait for completion
        fileDeleteExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("FileDeleteExchangeHandler should be completed after await", fileDeleteExchangeHandler.isCompleted());

        assertThat("FileDeleteExchangeHandler should contain the path in the affected files", fileDeleteExchangeHandler.getAffectedFilePaths(), hasItem(TEST_DIR_1.toString()));

        FileDeleteExchangeHandlerResult fileDeleteExchangeHandlerResult = fileDeleteExchangeHandler.getResult();

        assertNotNull("Result should not be null", fileDeleteExchangeHandlerResult);

        // now check on client2 that the directory and the contained file is removed
        assertFalse("TestDir1 is not removed", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR_1)));
        assertFalse("TestFile is not removed", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        // object store is notified using ObjectStoreListener

        // now check, that all delete events are ignored (incl children)
        IgnoreBusEvent expectedEvent1 = new IgnoreBusEvent(
                new DeleteEvent(
                        TEST_DIR_1,
                        TEST_DIR_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        IgnoreBusEvent expectedEvent2 = new IgnoreBusEvent(
                new DeleteEvent(
                        TEST_FILE_1,
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        List<IBusEvent> listener2Events = EVENT_BUS_LISTENER_2.getReceivedBusEvents();

        assertEquals("Listener should only contain both delete events", 2, listener2Events.size());

        IBusEvent actualEvent1 = listener2Events.get(0);
        IBusEvent actualEvent2 = listener2Events.get(1);

        assertEquals("Expected delete event", expectedEvent1.getEvent().getEventName(), actualEvent1.getEvent().getEventName());
        assertEquals("Expected path for testDir1", expectedEvent1.getEvent().getPath().toString(), actualEvent1.getEvent().getPath().toString());
        assertEquals("Expected name testDir1", expectedEvent1.getEvent().getName(), actualEvent1.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent1.getEvent().getHash(), actualEvent1.getEvent().getHash());

        assertEquals("Expected delete event", expectedEvent2.getEvent().getEventName(), actualEvent2.getEvent().getEventName());
        assertEquals("Expected path for testFile", expectedEvent2.getEvent().getPath().toString(), actualEvent2.getEvent().getPath().toString());
        assertEquals("Expected name testFile", expectedEvent2.getEvent().getName(), actualEvent2.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent2.getEvent().getHash(), actualEvent2.getEvent().getHash());
    }
}
