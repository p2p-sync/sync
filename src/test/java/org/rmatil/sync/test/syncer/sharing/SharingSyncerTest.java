package org.rmatil.sync.test.syncer.sharing;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.syncer.sharing.SharingSyncer;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class SharingSyncerTest extends BaseNetworkHandlerTest {

    protected static SharingSyncer SHARING_SYNCER;
    protected static Path TEST_DIR  = Paths.get("myDir");
    protected static Path TEST_FILE = TEST_DIR.resolve("myFile.txt");

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException {
        SHARING_SYNCER = new SharingSyncer(
                CLIENT_1,
                CLIENT_MANAGER_1,
                STORAGE_ADAPTER_1,
                OBJECT_STORE_1
        );

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                TEST_DIR.toString()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                TEST_FILE.toString()
        );
    }

    @Test
    public void testGetRelativePathToSharedFolder()
            throws InputOutputException {

        String relativePath = SHARING_SYNCER.getRelativePathToSharedFolder(TEST_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", TEST_DIR.toString(), relativePath);

        String relativePath2 = SHARING_SYNCER.getRelativePathToSharedFolder(TEST_FILE.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFile should be equal", TEST_FILE.toString(), relativePath2);

        OBJECT_STORE_1.getSharerManager().removeSharer(USER_2.getUserName(), TEST_FILE.toString());

        // add only read permissions to test file
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.READ, TEST_FILE.toString());
        String relativePath3 = SHARING_SYNCER.getRelativePathToSharedFolder(TEST_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", TEST_DIR.toString(), relativePath3);

        String relativePath4 = SHARING_SYNCER.getRelativePathToSharedFolder(TEST_FILE.getFileName().toString(), USER_2.getUserName(), AccessType.READ);
        assertEquals("RelativePathToSharedFile should be equal", TEST_FILE.getFileName().toString(), relativePath4);

    }
}

