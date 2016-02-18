package org.rmatil.sync.test.syncer.file;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.syncer.file.SyncFileChangeListener;
import org.rmatil.sync.core.syncer.sharing.SharingSyncer;
import org.rmatil.sync.core.syncer.sharing.event.ShareEvent;
import org.rmatil.sync.test.base.BaseIT;
import org.rmatil.sync.version.api.AccessType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class FileSyncerIT extends BaseIT {

    protected static ScheduledExecutorService EXECUTOR_SERVICE_1;
    protected static ScheduledExecutorService EXECUTOR_SERVICE_2;
    protected static ScheduledExecutorService EXECUTOR_SERVICE_3;
    protected static ScheduledExecutorService EXECUTOR_SERVICE_4;

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

        // start threads for all file syncer
        SyncFileChangeListener syncFileChangeListener1 = new SyncFileChangeListener(FILE_SYNCER_1);
        EXECUTOR_SERVICE_1 = Executors.newSingleThreadScheduledExecutor();
        EXECUTOR_SERVICE_1.scheduleAtFixedRate(syncFileChangeListener1, 0, 10, TimeUnit.SECONDS);
        GLOBAL_EVENT_BUS_1.subscribe(syncFileChangeListener1);

        SyncFileChangeListener syncFileChangeListener2 = new SyncFileChangeListener(FILE_SYNCER_2);
        EXECUTOR_SERVICE_2 = Executors.newSingleThreadScheduledExecutor();
        EXECUTOR_SERVICE_2.scheduleAtFixedRate(syncFileChangeListener2, 0, 10, TimeUnit.SECONDS);
        GLOBAL_EVENT_BUS_2.subscribe(syncFileChangeListener2);

	SyncFileChangeListener syncFileChangeListener3 = new SyncFileChangeListener(FILE_SYNCER_3);
        EXECUTOR_SERVICE_3 = Executors.newSingleThreadScheduledExecutor();
        EXECUTOR_SERVICE_3.scheduleAtFixedRate(syncFileChangeListener3, 0, 10, TimeUnit.SECONDS);
        GLOBAL_EVENT_BUS_3.subscribe(syncFileChangeListener3);

	SyncFileChangeListener syncFileChangeListener4 = new SyncFileChangeListener(FILE_SYNCER_4);
        EXECUTOR_SERVICE_4 = Executors.newSingleThreadScheduledExecutor();
        EXECUTOR_SERVICE_4.scheduleAtFixedRate(syncFileChangeListener4, 0, 10, TimeUnit.SECONDS);
        GLOBAL_EVENT_BUS_4.subscribe(syncFileChangeListener4);

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

    @AfterClass
    public static void tearDownChildIT() {
        EXECUTOR_SERVICE_1.shutdownNow();
        EXECUTOR_SERVICE_2.shutdownNow();
        EXECUTOR_SERVICE_3.shutdownNow();
        EXECUTOR_SERVICE_4.shutdownNow();
    }

    @Test
    public void test()
            throws IOException, InterruptedException {
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

        sharingSyncer.sync(shareEvent1);
        sharingSyncer.sync(shareEvent2);
        sharingSyncer.sync(shareEvent3);

        // after syncing, we should be able to tell, that at least one client of user2
        // has received the file

        // we check on client3, since he is the first client in the list of client locations from user2
        assertTrue("Test dir should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_DIR)));
        assertTrue("Test file1 should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1)));
        assertTrue("Test file2 should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_2)));

        System.err.println("Waiting, that shared files are propagate to the other client of user 2");

        // wait until file is also propagated to the second client of user2, i.e. client4
        Thread.sleep(60000L);

        assertTrue("Test dir should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_DIR)));
        assertTrue("Test file1 should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1)));
        assertTrue("Test file2 should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_2)));

    }
}