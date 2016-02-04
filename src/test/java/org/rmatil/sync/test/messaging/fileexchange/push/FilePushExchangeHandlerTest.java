package org.rmatil.sync.test.messaging.fileexchange.push;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandlerResult;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class FilePushExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path   TEST_DIR_1  = Paths.get("testDir1");
    protected static Path   TEST_DIR_2  = Paths.get("testDir2");
    protected static Path   TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static byte[] content     = new byte[10485739]; // 1024*1024*10 - 21
    protected static UUID   EXCHANGE_ID = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

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
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
                TEST_FILE_1.toString()
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, filePushExchangeHandler);

        Thread filePushExchangeHandlerThread = new Thread(filePushExchangeHandler);
        filePushExchangeHandlerThread.setName("TEST-FilePushExchangeHandler");
        filePushExchangeHandlerThread.start();

        // use a max of 30000 milliseconds to wait
        filePushExchangeHandler.await(30000L, TimeUnit.MILLISECONDS);

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
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
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

    @Test
    public void testSendFileWithChangesWhileTransferring()
            throws InterruptedException, IOException {
        FilePushExchangeHandler filePushExchangeHandler = new FilePushExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                CLIENT_LOCATIONS_1,
                TEST_FILE_1.toString()
        );

        UUID exchangeId = UUID.randomUUID();
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
                Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), alteredContent);
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
        byte[] actualContent = Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        assertArrayEquals("Content is not equal after changing the content while transferring", alteredContent, actualContent);
    }
}
