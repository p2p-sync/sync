package org.rmatil.sync.test.messaging.fileexchange.demand;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class FileDemandExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path   TEST_DIR_1        = Paths.get("testDir1");
    protected static Path   TEST_FILE_1       = TEST_DIR_1.resolve("testFile.txt");
    protected static Path   NON_EXISTING_FILE = TEST_DIR_1.resolve("blub_blub_blub.txt");
    protected static UUID   EXCHANGE_ID       = UUID.randomUUID();
    protected static byte[] FILE_CONTENT      = new byte[(FileDemandRequestHandler.CHUNK_SIZE * 2) + 15];

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        // create file only on client1
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        // write a bit more than two chunks to the file
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), FILE_CONTENT);

        OBJECT_STORE_1.sync();
        OBJECT_STORE_2.sync();
    }

    @Test
    public void test()
            throws InterruptedException, IOException {
        assertFalse("TestFile1 should not exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                STORAGE_ADAPTER_2,
                CLIENT_2,
                CLIENT_MANAGER_2,
                GLOBAL_EVENT_BUS_2,
                new NodeLocation(CLIENT_1.getUser().getUserName(), CLIENT_DEVICE_1.getClientDeviceId(), CLIENT_1.getPeerAddress()),
                TEST_FILE_1.toString(),
                EXCHANGE_ID
        );

        CLIENT_2.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileDemandExchangeHandler);

        Thread fileDemandExchangeHandlerThread = new Thread(fileDemandExchangeHandler);
        fileDemandExchangeHandlerThread.setName("TEST-FileDemandExchangeHandler");
        fileDemandExchangeHandlerThread.start();

        fileDemandExchangeHandler.await();

        assertTrue("Handler should be completed after awaiting", fileDemandExchangeHandler.isCompleted());
        assertNotNull("Result should not be null", fileDemandExchangeHandler.getResult());

        CLIENT_2.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        // now check that the file is written completely
        assertTrue("TestFile1 should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        // check that the content is equal
        assertArrayEquals("FileContent is not equal", FILE_CONTENT, Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        // now check, that all delete events are ignored (incl children)
        IgnoreBusEvent expectedEvent0 = new IgnoreBusEvent(
                new CreateEvent(
                        TEST_FILE_1,
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        AddSharerToObjectStoreBusEvent expectedEvent1 = new AddSharerToObjectStoreBusEvent(
                TEST_FILE_1.toString(),
                new HashSet<>()

        );

        IgnoreBusEvent expectedEvent2 = new IgnoreBusEvent(
                new ModifyEvent(
                        TEST_FILE_1,
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        IgnoreBusEvent expectedEvent3 = new IgnoreBusEvent(
                new ModifyEvent(
                        TEST_FILE_1,
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );


        List<IBusEvent> listener2Events = EVENT_BUS_LISTENER_2.getReceivedBusEvents();

        assertEquals("Listener should only contain all 4 events", 4, listener2Events.size());

        IBusEvent actualEvent0 = listener2Events.get(0);
        IBusEvent actualEvent1 = listener2Events.get(1);
        IBusEvent actualEvent2 = listener2Events.get(2);
        IBusEvent actualEvent3 = listener2Events.get(3);

        assertEquals("Expected create event", expectedEvent0.getEvent().getEventName(), actualEvent0.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent0.getEvent().getPath().toString(), actualEvent0.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent0.getEvent().getName(), actualEvent0.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent0.getEvent().getHash(), actualEvent0.getEvent().getHash());

        assertNull("Expected no attached event", actualEvent1.getEvent());
        assertThat("Expected event to be AddSharerToObjectStoreEvent", actualEvent1, is(instanceOf(AddSharerToObjectStoreBusEvent.class)));
        assertEquals("Expected path for testFile1", expectedEvent1.getRelativeFilePath(), ((AddSharerToObjectStoreBusEvent) actualEvent1).getRelativeFilePath());
        assertEquals("Expected no sharers", expectedEvent1.getSharers().size(), ((AddSharerToObjectStoreBusEvent) actualEvent1).getSharers().size());

        assertEquals("Expected modify event", expectedEvent2.getEvent().getEventName(), actualEvent2.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent2.getEvent().getPath().toString(), actualEvent2.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent2.getEvent().getName(), actualEvent2.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent2.getEvent().getHash(), actualEvent2.getEvent().getHash());

        assertEquals("Expected modify event", expectedEvent3.getEvent().getEventName(), actualEvent3.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent3.getEvent().getPath().toString(), actualEvent3.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent3.getEvent().getName(), actualEvent3.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent3.getEvent().getHash(), actualEvent3.getEvent().getHash());

        Files.delete(ROOT_TEST_DIR2.resolve(TEST_FILE_1));
    }


    @Test
    public void testFailing()
            throws InterruptedException {
        assertFalse("TestFile1 should not exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                STORAGE_ADAPTER_2,
                CLIENT_2,
                CLIENT_MANAGER_2,
                GLOBAL_EVENT_BUS_2,
                new NodeLocation(CLIENT_1.getUser().getUserName(), CLIENT_DEVICE_1.getClientDeviceId(), CLIENT_1.getPeerAddress()),
                NON_EXISTING_FILE.toString(),
                EXCHANGE_ID
        );

        CLIENT_2.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileDemandExchangeHandler);

        Thread fileDemandExchangeHandlerThread = new Thread(fileDemandExchangeHandler);
        fileDemandExchangeHandlerThread.setName("TEST-FileDemandExchangeHandler");
        fileDemandExchangeHandlerThread.start();

        fileDemandExchangeHandler.await();

        assertTrue("Handler should be completed after awaiting", fileDemandExchangeHandler.isCompleted());
        assertNotNull("Result should not be null", fileDemandExchangeHandler.getResult());

        CLIENT_2.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertFalse("File should not exist", Files.exists(ROOT_TEST_DIR2.resolve(NON_EXISTING_FILE)));
    }

    @Test
    public void testRetransmitOnWrongChecksum()
            throws InterruptedException, IOException {
        assertFalse("TestFile1 should not exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        byte[] largeContent = new byte[1024 * 1024 + 12]; // 11 chunks
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), largeContent);

        FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                STORAGE_ADAPTER_2,
                CLIENT_2,
                CLIENT_MANAGER_2,
                GLOBAL_EVENT_BUS_2,
                new NodeLocation(CLIENT_1.getUser().getUserName(), CLIENT_DEVICE_1.getClientDeviceId(), CLIENT_1.getPeerAddress()),
                TEST_FILE_1.toString(),
                EXCHANGE_ID
        );

        CLIENT_2.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileDemandExchangeHandler);

        final byte[] alteredContent = "This is some different content".getBytes();

        Thread fileDemandExchangeHandlerThread = new Thread(fileDemandExchangeHandler);
        fileDemandExchangeHandlerThread.setName("TEST-FileDemandExchangeHandler-ChangedFile");
        fileDemandExchangeHandlerThread.start();


        // just hope, that the file exchange is still running once we alter the content
        Thread changeFileThread = new Thread(() -> {
            try {
                // wait a bit until the first chunks have been written
                Thread.sleep(10);
                Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), alteredContent);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        changeFileThread.start();

        fileDemandExchangeHandler.await();

        assertTrue("Handler should be completed after awaiting", fileDemandExchangeHandler.isCompleted());
        assertNotNull("Result should not be null", fileDemandExchangeHandler.getResult());

        CLIENT_2.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        // now check that the file is written completely
        assertTrue("TestFile1 should not exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        // check that the content is equal
        assertArrayEquals("Altered content is not equal", alteredContent, Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));

        Files.delete(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        // we do not check the events here, since we may not be absolutely sure, when the file is altered and therefore
        // which event would contain the modify event for the new content
    }
}
