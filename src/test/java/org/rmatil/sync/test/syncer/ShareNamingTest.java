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
    protected static Path SHARED_DIR       = TEST_DIR_1.resolve("myDir");
    protected static Path SHARED_FILE      = SHARED_DIR.resolve("myFile.txt");

    protected static Path SOME_DIR             = Paths.get("someDir");
    protected static Path SOME_INNER_DIR       = SOME_DIR.resolve("innerDir");
    protected static Path SOME_INNER_INNER_DIR = SOME_INNER_DIR.resolve("innerDir2");
    protected static Path SOME_FILE            = SOME_INNER_INNER_DIR.resolve("someFile.txt");

    @Before
    public void before()
            throws IOException, InputOutputException, InterruptedException {

        deleteTestDirs();

        createTestDirs();
        createObjectStoreDirs();

        createSharedDirsIfNotExisting(ROOT_TEST_DIR1);

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR1.resolve(SHARED_DIR));
        Files.createFile(ROOT_TEST_DIR1.resolve(SHARED_FILE));

        Files.createDirectory(ROOT_TEST_DIR1.resolve(SOME_DIR));
        Files.createDirectory(ROOT_TEST_DIR1.resolve(SOME_INNER_DIR));
        Files.createDirectory(ROOT_TEST_DIR1.resolve(SOME_INNER_INNER_DIR));
        Files.createDirectory(ROOT_TEST_DIR1.resolve(SOME_FILE));

        OBJECT_STORE_1.getObjectManager().clear();
        OBJECT_STORE_1.sync();

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SHARED_DIR.toString()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SHARED_FILE.toString()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SOME_FILE.toString()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SOME_INNER_INNER_DIR.toString()
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

        // try to get the name of a path which is not shared at all
        String relativePath00 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_DIR_1.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("Unshared path should be filename", TEST_DIR_1.getFileName().toString(), relativePath00);

        // ok, add the sharer to it
        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                TEST_DIR_1.toString()
        );

        // this should be still the same
        relativePath00 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_DIR_1.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("Unshared path should be filename", TEST_DIR_1.getFileName().toString(), relativePath00);

        OBJECT_STORE_1.getSharerManager().removeSharer(
                USER_2.getUserName(),
                TEST_DIR_1.toString()
        );

        String relativePath0 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SOME_FILE.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePath should not be the whole path", SOME_INNER_INNER_DIR.getFileName().resolve(SOME_FILE.getFileName()).toString(), relativePath0);

        String relativePath = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", SHARED_DIR.getFileName().toString(), relativePath);

        String relativePath2 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_FILE.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFile should be equal", SHARED_DIR.getFileName().resolve(SHARED_FILE.getFileName()).toString(), relativePath2);

        OBJECT_STORE_1.getSharerManager().removeSharer(USER_2.getUserName(), SHARED_FILE.toString());

        // add only read permissions to test file
        OBJECT_STORE_1.getSharerManager().removeSharer(USER_2.getUserName(), SHARED_FILE.toString());
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.READ, SHARED_FILE.toString());
        String relativePath3 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("RelativePathToSharedFolder should be equal", SHARED_DIR.getFileName().toString(), relativePath3);

        String relativePath4 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_FILE.toString(), USER_2.getUserName(), AccessType.READ);
        assertEquals("RelativePathToSharedFile should be equal", SHARED_FILE.getFileName().toString(), relativePath4);

        // re-add write permissions
        OBJECT_STORE_1.getSharerManager().removeSharer(USER_2.getUserName(), SHARED_FILE.toString());
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, SHARED_FILE.toString());

        // now share all files up to the root
        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SOME_INNER_DIR.toString()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                USER_2.getUserName(),
                AccessType.WRITE,
                SOME_DIR.toString()
        );

        String relativePath5 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SOME_INNER_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("Path should be the same", SOME_INNER_DIR.toString(), relativePath5);

        String relativePath6 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SOME_DIR.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("Path should be the same", SOME_DIR.toString(), relativePath6);

        // check that the whole path is shared
        String relativePath7 = ShareNaming.getRelativePathToSharedFolderBySharer(STORAGE_ADAPTER_1, OBJECT_STORE_1, SOME_FILE.toString(), USER_2.getUserName(), AccessType.WRITE);
        assertEquals("Path should be the same", SOME_FILE.toString(), relativePath7);
    }

    @Test
    public void testGetRelativePathToSharedFolderByOwner()
            throws InputOutputException {
        OBJECT_STORE_1.getSharerManager().addOwner(
                USER_1.getUserName(),
                SHARED_DIR.toString()
        );

        OBJECT_STORE_1.getSharerManager().addOwner(
                USER_1.getUserName(),
                SHARED_FILE.toString()
        );

        String relativePath0 = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_DIR.toString(), USER_1.getUserName());
        assertEquals("RelativePath should be the name of the sharedDir", SHARED_DIR.getFileName().toString(), relativePath0);

        String relativePath = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_FILE.toString(), USER_1.getUserName());
        assertEquals("RelativePath should be sharedDir/sharedFile", SHARED_DIR.getFileName().resolve(SHARED_FILE.getFileName()).toString(), relativePath);

        OBJECT_STORE_1.getSharerManager().removeOwner(SHARED_DIR.toString());

        // path should now only be sharedFile
        String relativePath1 = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, SHARED_FILE.toString(), USER_1.getUserName());
        assertEquals("RelativePath should be sharedFile", SHARED_FILE.getFileName().toString(), relativePath1);

        // check a file which does not have an owner
        String relativePath2 = ShareNaming.getRelativePathToSharedFolderByOwner(STORAGE_ADAPTER_1, OBJECT_STORE_1, TEST_UNIQUE_FILE.toString(), USER_1.getUserName());
        assertEquals("RelativePath should be filename", TEST_UNIQUE_FILE.getFileName().toString(), relativePath2);
    }

}
