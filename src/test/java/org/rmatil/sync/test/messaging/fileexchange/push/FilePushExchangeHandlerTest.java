package org.rmatil.sync.test.messaging.fileexchange.push;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.eventbus.AddOwnerAndAccessTypeToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class FilePushExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path   TEST_DIR_1  = Paths.get("testDir1");
    protected static Path   TEST_DIR_2  = Paths.get("testDir2");
    protected static Path   TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static Path   TEST_FILE_2 = TEST_DIR_1.resolve("myFile2.txt");
    protected static byte[] content     = new byte[10485739]; // 1024*1024*10 - 21

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_2));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        RandomAccessFile randomAccessFile = new RandomAccessFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1).toString(), "rw");
        randomAccessFile.write(content);
        randomAccessFile.close();

        RandomAccessFile randomAccessFile2 = new RandomAccessFile(ROOT_TEST_DIR1.resolve(TEST_FILE_2).toString(), "rw");
        randomAccessFile2.write(content);
        randomAccessFile2.close();
    }

    @Before
    public void before()
            throws IOException {
        EVENT_BUS_LISTENER_1.clear();
        EVENT_BUS_LISTENER_2.clear();
    }

    @After
    public void after() {
        EVENT_BUS_LISTENER_1.clear();
        EVENT_BUS_LISTENER_2.clear();
    }

    @Test
    public void testSendFile()
            throws InterruptedException, IOException {
        assertEquals("No events should be contained", 0, EVENT_BUS_LISTENER_2.getReceivedBusEvents().size());

        UUID exchangeId = UUID.randomUUID();

        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
                TEST_FILE_1.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, filePushExchangeHandler);

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler");
        filePushExchangeHandlerThread.start();

        // use a max of 30000 milliseconds to wait
        filePushExchangeHandler.await(30000L, TimeUnit.MILLISECONDS);

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("FilePushExchangeHandler should be completed after awaiting", filePushExchangeHandler.isCompleted());

        FilePushExchangeHandlerResult filePushExchangeHandlerResult = filePushExchangeHandler.getResult();

        assertNotNull("Result should not be null", filePushExchangeHandlerResult);

        // now check both file contents
        byte[] expectedContent = Files.readAllBytes(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        byte[] actualContent = Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        assertArrayEquals("Content is not equal", expectedContent, actualContent);

        // assert that all required events are received
        // 10 chunks are expected
        // 2 events for owner resp sharers
        List<IBusEvent> busEvents = EVENT_BUS_LISTENER_2.getReceivedBusEvents();
        assertEquals("There should be ignore events for 10 chunks plus 2 for adding the owner and sharers", 12, busEvents.size());

        assertThat("First event should be AddOwnerAndAccessTypeToObjectStoreBusEvent", busEvents.get(0), is(instanceOf(AddOwnerAndAccessTypeToObjectStoreBusEvent.class)));
        assertThat("2nd event should be AddSharerToObjectStoreBusEvent", busEvents.get(1), is(instanceOf(AddSharerToObjectStoreBusEvent.class)));
        assertThat("3rd event should be IgnoreBusEvent", busEvents.get(2), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("3rd event should be IgnoreBusEvent (CreateEvent)", busEvents.get(2).getEvent(), is(instanceOf(CreateEvent.class)));
        assertThat("4th event should be IgnoreBusEvent", busEvents.get(3), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("4th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(3).getEvent(), is(instanceOf(ModifyEvent.class)));
        assertThat("5th event should be IgnoreBusEvent", busEvents.get(4), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("5th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(4).getEvent(), is(instanceOf(ModifyEvent.class)));
        assertThat("6th event should be IgnoreBusEvent", busEvents.get(5), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("6th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(5).getEvent(), is(instanceOf(ModifyEvent.class)));
        assertThat("7th event should be IgnoreBusEvent", busEvents.get(6), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("7th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(6).getEvent(), is(instanceOf(ModifyEvent.class)));
        assertThat("8th event should be IgnoreBusEvent", busEvents.get(7), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("8th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(7).getEvent(), is(instanceOf(ModifyEvent.class)));
        assertThat("9th event should be IgnoreBusEvent", busEvents.get(8), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("9th event should be IgnoreBusEvent (ModifyEvent)", busEvents.get(8).getEvent(), is(instanceOf(ModifyEvent.class)));
    }

    @Test
    public void testSendDirectory()
            throws InterruptedException, IOException {
        assertEquals("No events should be contained", 0, EVENT_BUS_LISTENER_2.getReceivedBusEvents().size());

        UUID exchangeId = UUID.randomUUID();

        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
                TEST_DIR_2.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, filePushExchangeHandler);

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler");
        filePushExchangeHandlerThread.start();

        filePushExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("FilePushExchangeHandler should be completed after awaiting", filePushExchangeHandler.isCompleted());

        FilePushExchangeHandlerResult filePushExchangeHandlerResult = filePushExchangeHandler.getResult();

        assertNotNull("Result should not be null", filePushExchangeHandlerResult);

        // now check both file contents
        assertTrue("Directory should be created on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR_2)));

        // assert that all required events are received
        // 1 chunk is expected
        // 2 events for owner resp sharers
        List<IBusEvent> busEvents = EVENT_BUS_LISTENER_2.getReceivedBusEvents();
        assertEquals("There should be ignore events for 1 chunk plus 2 for adding the owner and sharers", 3, busEvents.size());

        assertThat("First event should be AddOwnerAndAccessTypeToObjectStoreBusEvent", busEvents.get(0), is(instanceOf(AddOwnerAndAccessTypeToObjectStoreBusEvent.class)));
        assertThat("2nd event should be AddSharerToObjectStoreBusEvent", busEvents.get(1), is(instanceOf(AddSharerToObjectStoreBusEvent.class)));
        assertThat("3rd event should be IgnoreBusEvent", busEvents.get(2), is(instanceOf(IgnoreBusEvent.class)));
        assertThat("3rd event should be IgnoreBusEvent (CreateEvent)", busEvents.get(2).getEvent(), is(instanceOf(CreateEvent.class)));
    }

    @Test
    public void testSendFileWithChangesWhileTransferring()
            throws InterruptedException, IOException {
        assertEquals("No events should be contained", 0, EVENT_BUS_LISTENER_2.getReceivedBusEvents().size());

        UUID exchangeId = UUID.randomUUID();

        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
                TEST_FILE_2.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, filePushExchangeHandler);

        byte[] alteredContent = "Some altered content, forcing a re-download of the whole chunk".getBytes();

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler-ChangedFile");
        filePushExchangeHandlerThread.start();

        // just hope, that the file exchange is still running once we alter the content
        Thread changeFileThread = new Thread(() -> {

            try {
                // wait a bit so that the first chunk of the non-modified file could've been transferred
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            try {
                Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_2), alteredContent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        changeFileThread.start();

        // use a max of 30000 milliseconds to wait
        filePushExchangeHandler.await(30000L, TimeUnit.MILLISECONDS);

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("FilePushExchangeHandler should be completed after awaiting", filePushExchangeHandler.isCompleted());

        FilePushExchangeHandlerResult filePushExchangeHandlerResult = filePushExchangeHandler.getResult();

        assertNotNull("Result should not be null", filePushExchangeHandlerResult);

        // now check both file contents
        byte[] actualContent = Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_2));

        assertArrayEquals("Content is not equal after changing the content while transferring", alteredContent, actualContent);
    }
}
