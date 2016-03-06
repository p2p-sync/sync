package org.rmatil.sync.test.syncer;

import org.junit.Before;
import org.junit.Test;
import org.rmatil.sync.core.ShareNaming;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class ShareNamingTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1       = Paths.get("testDir1");
    protected static Path TEST_UNIQUE_FILE = TEST_DIR_1.resolve("myUniqueFile.txt");
    protected static Path TEST_UNIQUE_DIR  = TEST_DIR_1.resolve("myUniqueDir");
    protected static Path TEST_DIR         = TEST_DIR_1.resolve("myDir");
    protected static Path TEST_FILE        = TEST_DIR.resolve("myFile.txt");

    @Before
    public void before()
            throws IOException, InputOutputException, InterruptedException {

        deleteTestDirs();

        createTestDirs();
        createObjectStoreDirs();

        createSharedDirsIfNotExisting(ROOT_TEST_DIR1);

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR));
        Files.createFile(ROOT_TEST_DIR1.resolve(TEST_FILE));

        OBJECT_STORE_1.getObjectManager().clear();
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

        OBJECT_STORE_1.getSharerManager().addOwner(
                USER_1.getUserName(),
                TEST_DIR.toString()
        );

        OBJECT_STORE_1.getSharerManager().addOwner(
                USER_1.getUserName(),
                TEST_FILE.toString()
        );
    }

    @Test
    public void testGetUniqueFileName()
            throws InputOutputException, IOException {

        String uniqueFile = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_FILE.toString(), true);
        assertEquals("Filename should be equal before a file exists", TEST_UNIQUE_FILE.toString(), uniqueFile);

        Files.write(ROOT_TEST_DIR1.resolve(TEST_UNIQUE_FILE), "blub".getBytes());

        String uniqueFile2 = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_FILE.toString(), true);
        assertEquals("Filename should be different after a file has been written", TEST_DIR_1.toString() + "/myUniqueFile (1).txt", uniqueFile2);

        Files.write(ROOT_TEST_DIR1.resolve(uniqueFile2), "blub2".getBytes());
        String uniqueFile3 = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_FILE.toString(), true);
        assertEquals("Filename should be different after a file has been written", TEST_DIR_1.toString() + "/myUniqueFile (2).txt", uniqueFile3);

        // test directories
        String uniqueDir = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_DIR.toString(), false);
        assertEquals("Filename should be equal before a file exists", TEST_UNIQUE_DIR.toString(), uniqueDir);

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_UNIQUE_DIR));

        String uniqueDir2 = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_DIR.toString(), false);
        assertEquals("Filename should be different after a file has been written", TEST_DIR_1.toString() + "/myUniqueDir (1)", uniqueDir2);

        Files.createDirectory(ROOT_TEST_DIR1.resolve(uniqueDir2));
        String uniqueDir3 = ShareNaming.getUniqueFileName(STORAGE_ADAPTER_1, TEST_UNIQUE_DIR.toString(), false);
        assertEquals("Filename should be different after a file has been written", TEST_DIR_1.toString() + "/myUniqueDir (2)", uniqueDir3);
    }

    @Test
    public void testGetRelativePathToSharedFolder()
            throws InputOutputException, IOException {

        String relativePath = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", TEST_DIR.toString(), relativePath);

        String relativePath2 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_FILE.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFile should be equal", TEST_FILE.toString(), relativePath2);

        OBJECT_STORE_1.getSharerManager().removeSharer(USER_2.getUserName(), TEST_FILE.toString());

        // add only read permissions to test file
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.READ, TEST_FILE.toString());
        String relativePath3 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", TEST_DIR.toString(), relativePath3);

        String relativePath4 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_FILE.getFileName().toString(), USER_2.getUserName(), AccessType.READ);
        assertEquals("RelativePathToSharedFile should be equal", TEST_FILE.getFileName().toString(), relativePath4);
    }

    @Test
    public void testGetRelativePathToSharedFolderByOwner()
            throws InputOutputException {
        String relativePath = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_DIR.toString(), USER_1.getUserName());
        assertEquals("RelativePathToSharedFolder should be equal", TEST_DIR.toString(), relativePath);

        String relativePath2 = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_FILE.toString(), USER_1.getUserName());
        assertEquals("RelativePathToSharedFile should be equal", TEST_FILE.toString(), relativePath2);

        String relativePath3 = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_UNIQUE_FILE.toString(), USER_1.getUserName());
        assertEquals("RelativePath should be up to the top level, i.e. including the testDir", TEST_UNIQUE_FILE.toString(), relativePath3);
    }

}
