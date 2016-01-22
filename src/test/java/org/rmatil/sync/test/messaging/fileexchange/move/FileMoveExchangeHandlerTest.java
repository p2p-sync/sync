package org.rmatil.sync.test.messaging.fileexchange.move;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.core.model.PathObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class FileMoveExchangeHandlerTest extends BaseNetworkHandlerTest {


    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static Path TEST_DIR_2  = TEST_DIR_1.resolve("testDir2");
    protected static Path TEST_FILE_1 = TEST_DIR_2.resolve("myFile.txt");
    protected static Path TEST_FILE_2 = TEST_DIR_1.resolve("myFile2.txt");
    protected static Path TEST_FILE_3 = Paths.get("myFile3.txt");
    protected static Path TARGET_DIR  = Paths.get("targetDir");
    protected static UUID EXCHANGE_ID = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_2));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_2));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_2));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TARGET_DIR));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TARGET_DIR));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_3));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_3));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());
    }

    @Test
    public void testMoveDirectory()
            throws InterruptedException, InputOutputException {

        // move
        STORAGE_ADAPTER_1.move(StorageType.DIRECTORY, new LocalPathElement(TEST_DIR_1.toString()), new LocalPathElement(TARGET_DIR.resolve(TEST_DIR_1).toString()));
        // rebuild object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());


        MoveEvent moveEvent = new MoveEvent(
                TEST_DIR_1,
                TARGET_DIR.resolve(TEST_DIR_1),
                TEST_DIR_1.getFileName().toString(),
                "noNeedToKnowTheHashCurrently",
                System.currentTimeMillis()
        );

        FileMoveExchangeHandler fileMoveExchangeHandler = new FileMoveExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                GLOBAL_EVENT_BUS_1,
                CLIENT_LOCATIONS,
                moveEvent
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileMoveExchangeHandler);

        Thread fileMoveExchangeHandlerThread = new Thread(fileMoveExchangeHandler);
        fileMoveExchangeHandlerThread.setName("TEST-FileOfferExchangeHandler");
        fileMoveExchangeHandlerThread.start();

        // wait for completion
        fileMoveExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("FileOfferExchangeHandler should be completed after wait", fileMoveExchangeHandler.isCompleted());

        FileMoveExchangeHandlerResult result = fileMoveExchangeHandler.getResult();

        assertNotNull("FileMoveExchangeHandlerResult should not be null", result);

        // check if moving is done on client2
        assertTrue("TestDir1 is not moved", Files.exists(ROOT_TEST_DIR2.resolve(TARGET_DIR).resolve(TEST_DIR_1)));
        assertTrue("TestDir2 is not moved", Files.exists(ROOT_TEST_DIR2.resolve(TARGET_DIR).resolve(TEST_DIR_2)));
        assertTrue("testFile1 is not moved", Files.exists(ROOT_TEST_DIR2.resolve(TARGET_DIR).resolve(TEST_FILE_1)));
        assertTrue("TestDir1 is not moved", Files.exists(ROOT_TEST_DIR2.resolve(TARGET_DIR).resolve(TEST_FILE_2)));

        assertFalse("Old path should not be present anymore", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR_1)));
    }

    @Test
    public void testMoveFile()
            throws InputOutputException, InterruptedException {
        // move
        STORAGE_ADAPTER_1.move(StorageType.FILE, new LocalPathElement(TEST_FILE_3.toString()), new LocalPathElement(TARGET_DIR.resolve(TEST_FILE_3).toString()));
        // rebuild object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        PathObject pathObject = OBJECT_STORE_1.getObjectManager().getObjectForPath(TARGET_DIR.resolve(TEST_FILE_3).toString());

        MoveEvent moveEvent = new MoveEvent(
                TEST_FILE_3,
                TARGET_DIR.resolve(TEST_FILE_3),
                TEST_FILE_3.getFileName().toString(),
                pathObject.getVersions().get(Math.max(0, pathObject.getVersions().size() - 1)).getHash(),
                System.currentTimeMillis()
        );

        FileMoveExchangeHandler fileMoveExchangeHandler = new FileMoveExchangeHandler(
                EXCHANGE_ID,
                CLIENT_DEVICE_1,
                STORAGE_ADAPTER_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                GLOBAL_EVENT_BUS_1,
                CLIENT_LOCATIONS,
                moveEvent
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, fileMoveExchangeHandler);

        Thread fileMoveExchangeHandlerThread = new Thread(fileMoveExchangeHandler);
        fileMoveExchangeHandlerThread.setName("TEST-FileOfferExchangeHandler");
        fileMoveExchangeHandlerThread.start();

        // wait for completion
        fileMoveExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("FileOfferExchangeHandler should be completed after wait", fileMoveExchangeHandler.isCompleted());

        FileMoveExchangeHandlerResult result = fileMoveExchangeHandler.getResult();

        assertNotNull("FileMoveExchangeHandlerResult should not be null", result);

        // check if moving is done on client2
        assertTrue("TestFile3 is not moved", Files.exists(ROOT_TEST_DIR2.resolve(TARGET_DIR).resolve(TEST_FILE_3)));
        assertFalse("Old path should not be present anymore", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_3)));
    }
}
