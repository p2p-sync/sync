package org.rmatil.sync.test.syncer.file;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.syncer.sharing.SharingSyncer;
import org.rmatil.sync.core.syncer.sharing.event.ShareEvent;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.base.BaseIT;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.PathType;
import org.rmatil.sync.version.core.model.PathObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.*;

public class FileSyncerIT extends BaseIT {

    /**
     * The chunk size is 1MB. Therefore, to force sending multiple
     * chunks, we have to create a string longer than 1024*1024 / 2,
     * since a char is 2 bytes long
     */
    protected static byte[] FILE_CONTENT;

    protected static final Path TEST_DIR    = Paths.get("myDir");
    protected static final Path TEST_FILE_1 = TEST_DIR.resolve(Paths.get("myFile.txt"));
    protected static final Path TEST_FILE_2 = TEST_DIR.resolve(Paths.get("myFile2.txt"));


    @BeforeClass
    public static void setUpChildIT()
            throws IOException, InterruptedException {

        FILE_CONTENT = createFileContent();

        EVENT_AGGREGATOR_1.start();
        EVENT_AGGREGATOR_2.start();
        EVENT_AGGREGATOR_3.start();
        EVENT_AGGREGATOR_4.start();

        // wait a little to allow event aggregators to start
        Thread.sleep(100L);
    }

    /**
     * Returns 1024 * 1024 * 3 - 12 bytes of text
     *
     * @return The text created
     *
     * @throws IOException If serializing to bytes failed
     */
    private static byte[] createFileContent()
            throws IOException {
        return Files.readAllBytes(Paths.get("./src/main/resources/test-file-content.txt"));
    }

    @Test
    public void test()
            throws IOException, InterruptedException, InputOutputException {
        // first create the file on a client (client1) of user1,
        // and sync it to its other client (client2)

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR));
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_2), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // now the event aggregator should pick up three create events
        // and sync them to the other client

        // time until event aggregator has started to persist changes in object store
        Thread.sleep(EVENT_AGGREGATOR_1.getAggregationInterval());
        // time until file syncer has been started
        Thread.sleep(10000L);
        // now wait for file offering to complete
        Thread.sleep(20000L);
        // wait for file transfer to complete
        Thread.sleep(30000L);

        // check whether the file exists on client2
        assertTrue("Test dir should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR)));
        assertTrue("Test file1 should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));
        assertTrue("Test file2 should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_2)));

        // ok, now try to share the test dir recursively
        System.err.println("Starting to share files with a client of user2");

        SharingSyncer sharingSyncer = new SharingSyncer(CLIENT_1, CLIENT_MANAGER_1, STORAGE_ADAPTER_1, OBJECT_STORE_1);

        IShareEvent shareEvent1 = new ShareEvent(TEST_DIR, AccessType.WRITE, USER_2.getUserName());
        IShareEvent shareEvent2 = new ShareEvent(TEST_FILE_1, AccessType.WRITE, USER_2.getUserName());
        IShareEvent shareEvent3 = new ShareEvent(TEST_FILE_2, AccessType.WRITE, USER_2.getUserName());

        // after syncing, we should be able to tell, that at least one client of user2
        // has received the file

        // we check on client3, since he is the first client in the list of client locations from user2
        sharingSyncer.sync(shareEvent1);
        Path expectedTestDir = ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_DIR);
        assertTrue("Test dir should exist on client3", Files.exists(expectedTestDir));
        System.err.println("Waiting, that testDir is propagated to the other client of user 2");
        Thread.sleep(60000L);
        assertTrue("Test dir should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_DIR)));
        // check that no conflict file was created
        assertEquals("Only one file should be created inside the root test dir", 1, ROOT_TEST_DIR4.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).toFile().listFiles().length);
        // check object store
        PathObject dirObject = OBJECT_STORE_4.getObjectManager().getObjectForPath(Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + TEST_DIR);
        assertNotNull("Pathobject for testDir should not be null", dirObject);
        assertEquals("Owner should be user1", USER_1.getUserName(), dirObject.getOwner());
        assertEquals("AccessType should be write", AccessType.WRITE, dirObject.getAccessType());
        assertThat("No sharer should be inside", dirObject.getSharers(), is(empty()));
        assertEquals("PathType should be dir", PathType.DIRECTORY, dirObject.getPathType());
        assertEquals("Only one version should be contained", 1, dirObject.getVersions().size());


        sharingSyncer.sync(shareEvent2);
        Path expectedTestFile1 = ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1);
        assertTrue("Test file1 should exist on client3", Files.exists(expectedTestFile1));
        System.err.println("Waiting, that testFile is propagated to the other client of user 2");
        Thread.sleep(60000L);
        assertTrue("Test file1 should exist on client4", Files.exists(expectedTestFile1));
        // check that no conflict file was created
        assertEquals("Only one file should be contained in the test dir", 1, expectedTestDir.toFile().listFiles().length);
        // check object store
        PathObject file1Object = OBJECT_STORE_4.getObjectManager().getObjectForPath(Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + TEST_FILE_1);
        assertNotNull("Pathobject for testFile1 should not be null", file1Object);
        assertEquals("Owner should be user1", USER_1.getUserName(), file1Object.getOwner());
        assertEquals("AccessType should be write", AccessType.WRITE, file1Object.getAccessType());
        assertThat("No sharer should be inside", file1Object.getSharers(), is(empty()));
        assertEquals("PathType should be file", PathType.FILE, file1Object.getPathType());
        assertEquals("Only one version should be contained", 1, file1Object.getVersions().size());


        sharingSyncer.sync(shareEvent3);
        Path expectedTestFile2 = ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_2);
        assertTrue("Test file2 should exist on client3", Files.exists(expectedTestFile2));
        System.err.println("Waiting, that testFile2 is propagated to the other client of user 2");
        Thread.sleep(60000L);
        assertTrue("Test file2 should exist on client4", Files.exists(expectedTestFile2));
        // check that no conflict file was created
        assertEquals("Only two files should be contained in the test dir (file1 & file2)", 2, expectedTestDir.toFile().listFiles().length);
        // check object store
        PathObject file2Object = OBJECT_STORE_4.getObjectManager().getObjectForPath(Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + TEST_FILE_2);
        assertNotNull("Pathobject for testFile2 should not be null", file2Object);
        assertEquals("Owner should be user1", USER_1.getUserName(), file2Object.getOwner());
        assertEquals("AccessType should be write", AccessType.WRITE, file2Object.getAccessType());
        assertThat("No sharer should be inside", file2Object.getSharers(), is(empty()));
        assertEquals("PathType should be file", PathType.FILE, file2Object.getPathType());
        assertEquals("Only one version should be contained", 1, file2Object.getVersions().size());
    }
}
