package org.rmatil.sync.test.messaging.fileexchange.offer;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandlerResult;
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

public class FileOfferExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static Path TEST_DIR_2  = Paths.get("testDir2");
    protected static Path TEST_FILE_1 = TEST_DIR_1.resolve("myFile.txt");
    protected static Path TEST_FILE_2 = TEST_DIR_2.resolve("myFile2.txt");
    protected static Path TEST_FILE_3 = TEST_DIR_2.resolve("myFile3.txt");
    protected static Path TARGET_DIR  = Paths.get("targetDir");

    protected static UUID exchangeId = UUID.randomUUID();
    protected static MoveEvent                moveDirEvent;
    protected static MoveEvent                moveFileEvent;
    protected static MoveEvent                moveConflictFileEvent;
    protected static FileOfferExchangeHandler dirOfferExchangeHandler;
    protected static FileOfferExchangeHandler fileOfferExchangeHandler;
    protected static FileOfferExchangeHandler conflictFileOfferExchangeHandler;

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {

        // create some test files and dirs to move

        // -- testDir1 // mv to targetDir
        //  | |
        //  | |--- myFile.txt
        //  |
        //  - testDir2
        //  | |
        //  | |--- myFile2.txt // mv to targetDir
        //  | |--- myFile3.txt // mv to targetDir after creating conflict manually
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

        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE_3));
        Files.createFile(ROOT_TEST_DIR2.resolve(TEST_FILE_3));

        // create directory to where the files should be moved
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TARGET_DIR));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TARGET_DIR));

        // force recreation of object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());

        // do not start the event aggregator but manually sync the event
        String hashToDir = OBJECT_STORE_1.getObjectManager().getIndex().getPaths().get(TEST_DIR_1.toString());
        PathObject pathObject = OBJECT_STORE_1.getObjectManager().getObject(hashToDir);

        moveDirEvent = new MoveEvent(
                TEST_DIR_1,
                TARGET_DIR,
                TARGET_DIR.getFileName().toString(),
                pathObject.getVersions().get(Math.max(pathObject.getVersions().size() - 1, 0)).getHash(),
                System.currentTimeMillis()
        );

        String hashToFile = OBJECT_STORE_1.getObjectManager().getIndex().getPaths().get(TEST_FILE_2.toString());
        PathObject fileObject = OBJECT_STORE_1.getObjectManager().getObject(hashToFile);

        moveFileEvent = new MoveEvent(
                TEST_FILE_2,
                TARGET_DIR.resolve(TEST_FILE_2.getFileName().toString()),
                TEST_FILE_2.getFileName().toString(),
                fileObject.getVersions().get(Math.max(fileObject.getVersions().size() -1 , 0)).getHash(),
                System.currentTimeMillis()
        );

        moveConflictFileEvent = new MoveEvent(
                TEST_FILE_3,
                TARGET_DIR.resolve(TEST_FILE_3.getFileName().toString()),
                TEST_FILE_3.getFileName().toString(),
                fileObject.getVersions().get(Math.max(fileObject.getVersions().size() -1 , 0)).getHash(),
                System.currentTimeMillis()
        );

        dirOfferExchangeHandler = new FileOfferExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                STORAGE_ADAPTER_1,
                GLOBAL_EVENT_BUS_1,
                moveDirEvent
        );

        fileOfferExchangeHandler = new FileOfferExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                STORAGE_ADAPTER_1,
                GLOBAL_EVENT_BUS_1,
                moveFileEvent
        );

        conflictFileOfferExchangeHandler = new FileOfferExchangeHandler(
                exchangeId,
                CLIENT_DEVICE_1,
                CLIENT_MANAGER_1,
                CLIENT_1,
                OBJECT_STORE_1,
                STORAGE_ADAPTER_1,
                GLOBAL_EVENT_BUS_1,
                moveConflictFileEvent
        );
    }

    @Test
    public void testMoveDir()
            throws InterruptedException, InputOutputException {

        // first move the directory
        STORAGE_ADAPTER_1.move(StorageType.DIRECTORY, new LocalPathElement(TEST_DIR_1.toString()), new LocalPathElement(TARGET_DIR.toString()));
        // force recreation of object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, dirOfferExchangeHandler);

        Thread dirOfferExchangeHandlerThread = new Thread(dirOfferExchangeHandler);
        dirOfferExchangeHandlerThread.setName("TEST-DirOfferExchangeHandler");
        dirOfferExchangeHandlerThread.start();

        // wait for completion
        dirOfferExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("DirOfferExchangeHandler should be completed after wait", dirOfferExchangeHandler.isCompleted());

        FileOfferExchangeHandlerResult result = dirOfferExchangeHandler.getResult();

        assertTrue("Client2 should have accepted offer", result.hasOfferAccepted());
        assertFalse("Client2 should not have detected a conflict", result.hasConflictDetected());
    }

    @Test
    public void testMoveFile()
            throws InterruptedException, InputOutputException {

        // first move the file
        STORAGE_ADAPTER_1.move(StorageType.FILE, new LocalPathElement(TEST_FILE_2.toString()), new LocalPathElement(TARGET_DIR.resolve(TEST_FILE_2.getFileName().toString()).toString()));
        // force recreation of object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

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

    @Test
    public void testConflict()
            throws InputOutputException, InterruptedException {
        // first move the file
        STORAGE_ADAPTER_1.move(StorageType.FILE, new LocalPathElement(TEST_FILE_3.toString()), new LocalPathElement(TARGET_DIR.resolve(TEST_FILE_3.getFileName().toString()).toString()));
        // force recreation of object store
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        // now adjust the file on client2 to get a different version
        STORAGE_ADAPTER_2.persist(StorageType.FILE, new LocalPathElement(TEST_FILE_3.toString()), "Some different content causing a conflict".getBytes());
        STORAGE_ADAPTER_2.move(StorageType.FILE, new LocalPathElement(TEST_FILE_3.toString()), new LocalPathElement(TARGET_DIR.resolve(TEST_FILE_3.getFileName().toString()).toString()));
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, conflictFileOfferExchangeHandler);

        Thread conflictFileOfferExchangeHandlerThread = new Thread(conflictFileOfferExchangeHandler);
        conflictFileOfferExchangeHandlerThread.setName("TEST-ConflictFileOfferExchangeHandler");
        conflictFileOfferExchangeHandlerThread.start();

        // wait for completion
        conflictFileOfferExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        assertTrue("FileOfferExchangeHandler should be completed after wait", conflictFileOfferExchangeHandler.isCompleted());

        FileOfferExchangeHandlerResult result = conflictFileOfferExchangeHandler.getResult();

        assertTrue("Client2 should have accepted offer", result.hasOfferAccepted());
        assertTrue("Client2 should have detected a conflict", result.hasConflictDetected());
    }
}
