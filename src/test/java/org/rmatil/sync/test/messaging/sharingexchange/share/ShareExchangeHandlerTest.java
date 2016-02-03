package org.rmatil.sync.test.messaging.sharingexchange.share;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreObjectStoreUpdateBusEvent;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareExchangeHandlerResult;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.PathObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ShareExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path   TEST_DIR_1  = Paths.get("testDir1");
    protected static Path   TEST_DIR_2  = Paths.get("testDir2");
    protected static Path   TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static byte[] content     = new byte[(ShareExchangeHandler.CHUNK_SIZE * 2) + 15]; // 1024*1024*10 - 21
    protected static UUID   EXCHANGE_ID = UUID.randomUUID();
    protected static UUID   FILE_ID     = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InterruptedException, InputOutputException {
        CLIENT_2.shutdown();

        // wait a bit until client2 has correctly shutdown
        Thread.sleep(1000L);

        CLIENT_2 = createClient(USER_2, STORAGE_ADAPTER_2, OBJECT_STORE_2, GLOBAL_EVENT_BUS_2, PORT_CLIENT_2, new RemoteClientLocation(
                CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                CLIENT_1.getPeerAddress().isIPv6(),
                CLIENT_1.getPeerAddress().tcpPort()
        ));

        CLIENT_MANAGER_2 = CLIENT_2.getClientManager();


        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        // only create files on first client
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));


        RandomAccessFile randomAccessFile = new RandomAccessFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1).toString(), "rw");
        randomAccessFile.write(content);
        randomAccessFile.close();
    }

    @Test
    public void testSendFile()
            throws InterruptedException, IOException, InputOutputException {
        ShareExchangeHandler shareExchangeHandler = new ShareExchangeHandler(
                CLIENT_1,
                new ClientLocation(CLIENT_2.getClientDeviceId(), CLIENT_2.getPeerAddress()),
                STORAGE_ADAPTER_1,
                TEST_FILE_1.toString(),
                TEST_FILE_1.getFileName().toString(),
                AccessType.WRITE,
                FILE_ID,
                true,
                EXCHANGE_ID
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, shareExchangeHandler);

        Thread fileShareExchangeHandlerThread = new Thread(shareExchangeHandler);
        fileShareExchangeHandlerThread.setName("TEST-ShareExchangeHandler");
        fileShareExchangeHandlerThread.start();

        // use a max of 30000 milliseconds to wait
        shareExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("ShareExchangeHandler should be completed after awaiting", shareExchangeHandler.isCompleted());

        ShareExchangeHandlerResult shareExchangeHandlerResult = shareExchangeHandler.getResult();

        assertNotNull("Result should not be null", shareExchangeHandlerResult);

        // check that shared folders are created
        assertTrue("SharedWithOthers (READ) should exist", Files.exists(ROOT_TEST_DIR2.resolve(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName())));
        assertTrue("SharedWithOthers (READ-WRITE) should exist", Files.exists(ROOT_TEST_DIR2.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName())));

        assertTrue("File should exist in READ-WRITE folder", Files.exists(ROOT_TEST_DIR2.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName())));

        // now check both file contents
        byte[] expectedContent = Files.readAllBytes(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        byte[] actualContent = Files.readAllBytes(ROOT_TEST_DIR2.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName()));

        assertArrayEquals("Content is not equal", expectedContent, actualContent);

        // now check, that all delete events are ignored (incl children)
        CreateEvent createEvent = new CreateEvent(
                Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName()),
                TEST_FILE_1.getFileName().toString(),
                "weIgnoreTheHash",
                System.currentTimeMillis()
        );

        IgnoreBusEvent expectedEvent0 = new IgnoreBusEvent(
                createEvent
        );

        IgnoreObjectStoreUpdateBusEvent expectedEvent1 = new IgnoreObjectStoreUpdateBusEvent(
                createEvent
        );

        IgnoreBusEvent expectedEvent2 = new IgnoreBusEvent(
                new ModifyEvent(
                        Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName()),
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        IgnoreBusEvent expectedEvent3 = new IgnoreBusEvent(
                new ModifyEvent(
                        Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName()),
                        TEST_FILE_1.getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        );

        CreateBusEvent expectedEvent4 = new CreateBusEvent(
                createEvent
        );


        List<IBusEvent> listener2Events = EVENT_BUS_LISTENER_2.getReceivedBusEvents();

        assertEquals("Listener should only contain all 5 events", 5, listener2Events.size());

        IBusEvent actualEvent0 = listener2Events.get(0);
        IBusEvent actualEvent1 = listener2Events.get(1);
        IBusEvent actualEvent2 = listener2Events.get(2);
        IBusEvent actualEvent3 = listener2Events.get(3);
        IBusEvent actualEvent4 = listener2Events.get(4);

        assertEquals("Expected create event", expectedEvent0.getEvent().getEventName(), actualEvent0.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent0.getEvent().getPath().toString(), actualEvent0.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent0.getEvent().getName(), actualEvent0.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent0.getEvent().getHash(), actualEvent0.getEvent().getHash());

        assertEquals("Expected create event", expectedEvent1.getEvent().getEventName(), actualEvent1.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent1.getEvent().getPath().toString(), actualEvent1.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent1.getEvent().getName(), actualEvent1.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent1.getEvent().getHash(), actualEvent1.getEvent().getHash());

        assertEquals("Expected modify event", expectedEvent2.getEvent().getEventName(), actualEvent2.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent2.getEvent().getPath().toString(), actualEvent2.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent2.getEvent().getName(), actualEvent2.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent2.getEvent().getHash(), actualEvent2.getEvent().getHash());

        assertEquals("Expected modify event", expectedEvent3.getEvent().getEventName(), actualEvent3.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent3.getEvent().getPath().toString(), actualEvent3.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent3.getEvent().getName(), actualEvent3.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent3.getEvent().getHash(), actualEvent3.getEvent().getHash());

        assertEquals("Expected modify event", expectedEvent4.getEvent().getEventName(), actualEvent4.getEvent().getEventName());
        assertEquals("Expected path for testFile1", expectedEvent4.getEvent().getPath().toString(), actualEvent4.getEvent().getPath().toString());
        assertEquals("Expected name testFile1", expectedEvent4.getEvent().getName(), actualEvent4.getEvent().getName());
        assertEquals("Expected different hash", expectedEvent4.getEvent().getHash(), actualEvent4.getEvent().getHash());

        // now check that the object store contains the sharer
        PathObject sharedObject = OBJECT_STORE_2.getObjectManager().getObjectForPath(Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1.getFileName()).toString());

        assertNotNull("SharedObject should not be null", sharedObject);
        // since the client 2 did not share with anyone. Instead client1 shared with client2.
        // -> only the owner should be set
        assertFalse("File should not be shared", sharedObject.isShared());
        assertEquals("Owner should be equal to client1's user", CLIENT_1.getUser().getUserName(), sharedObject.getOwner());
        assertEquals("Sharer should not contain any user", 0, sharedObject.getSharers().size());
    }
}
