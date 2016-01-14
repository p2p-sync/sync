package org.rmatil.sync.test.messaging.fileexchange.push;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandlerResult;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class FilePushExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path   TEST_DIR_1  = Paths.get("testDir1");
    protected static Path   TEST_DIR_2  = Paths.get("testDir2");
    protected static Path   TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static byte[] content     = new byte[10485739]; // 1024*1024*10 - 21
    protected static UUID   EXCHANGE_ID = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));

        RandomAccessFile randomAccessFile = new RandomAccessFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1).toString(), "rw");
        randomAccessFile.write(content);
        randomAccessFile.close();
    }

    @Test
    public void testSendFile()
            throws InterruptedException, IOException {
        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                TEST_FILE_1.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, filePushExchangeHandler);

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler");
        filePushExchangeHandlerThread.start();

        filePushExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("FilePushExchangeHandler should be completed after awaiting", filePushExchangeHandler.isCompleted());

        FilePushExchangeHandlerResult filePushExchangeHandlerResult = filePushExchangeHandler.getResult();

        assertNotNull("Result should not be null", filePushExchangeHandlerResult);

        // now check both file contents
        byte[] expectedContent = Files.readAllBytes(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        byte[] actualContent = Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        assertArrayEquals("Content is not equal", expectedContent, actualContent);
    }

    @Test
    public void testSendDirectory()
            throws InterruptedException, IOException {
        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                TEST_DIR_2.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, filePushExchangeHandler);

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler");
        filePushExchangeHandlerThread.start();

        filePushExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("FilePushExchangeHandler should be completed after awaiting", filePushExchangeHandler.isCompleted());

        FilePushExchangeHandlerResult filePushExchangeHandlerResult = filePushExchangeHandler.getResult();

        assertNotNull("Result should not be null", filePushExchangeHandlerResult);

        // now check both file contents
        assertTrue("Directory should be created on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR_2)));
    }
}
