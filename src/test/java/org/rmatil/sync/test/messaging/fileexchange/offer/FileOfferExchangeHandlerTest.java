package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.core.model.PathObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class FileOfferExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static Path TEST_DIR_2  = Paths.get("testDir2");
    protected static Path TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static Path TEST_FILE_2 = TEST_DIR_2.resolve("myFile2.txt");
    protected static Path TARGET_DIR  = Paths.get("targetDir");

    protected static UUID exchangeId = UUID.randomUUID();
    protected static MoveEvent moveEvent;
    protected static FileOfferExchangeHandler fileOfferExchangeHandler;

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {

        // create some test files and dirs to move

        // -- testDir1
        //  | |
        //  |  --- myFile.txt
        //  |
        //  - testDir2
        //  | |
        //  |  --- myFile2.txt
        //  |
        //  - targetDir

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_2));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_1));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_1));

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_2));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_2));

        // create directory to where the files should be moved
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TARGET_DIR));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TARGET_DIR));

        // force recreation of object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());

        // do not start the event aggregator but manually sync the event
        String hashToFile = OBJECT_STORE_1.getObjectManager().getIndex().getPaths().get(TEST_DIR_1.toString());
        PathObject pathObject = OBJECT_STORE_1.getObjectManager().getObject(hashToFile);

        moveEvent = new MoveEvent(
                TEST_DIR_1,
                TARGET_DIR,
                TARGET_DIR.getFileName().toString(),
                pathObject.getVersions().get(Math.max(pathObject.getVersions().size() - 1, 0)).getHash(),
                System.currentTimeMillis()
        );

        fileOfferExchangeHandler = new FileOfferExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                STORAGE_ADAPTER_1,
                GLOBAL_EVENT_BUS_1,
                moveEvent
        );
    }

    @Test
    public void test()
            throws InterruptedException {
        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, fileOfferExchangeHandler);

        Thread fileOfferExchangeHandlerThread = new Thread(fileOfferExchangeHandler);
        fileOfferExchangeHandlerThread.setName("TEST-FileOfferExchangeHandler");
        fileOfferExchangeHandlerThread.start();

        // wait for completion
        fileOfferExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("FileOfferExchangeHandler should be completed after wait", fileOfferExchangeHandler.isCompleted());

        FileOfferExchangeHandlerResult result = fileOfferExchangeHandler.getResult();

        assertTrue("Client2 should have accepted offer", result.hasOfferAccepted());
        assertFalse("Client2 should not have detected a conflict", result.hasConflictDetected());
    }
}
