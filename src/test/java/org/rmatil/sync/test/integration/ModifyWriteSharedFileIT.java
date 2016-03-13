package org.rmatil.sync.test.integration;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.base.BaseIT;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.PathObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * This test ensures correct propagation of
 * file modifications made on shared files.
 * <p>
 * Setup:
 * <p>
 * User1: Client1 and Client2
 * User2: Client3 and Client4
 * <p>
 * Procedure:
 * A directory containing 2 files is shared. Owner
 * is user1.
 * Client4 modifies one file. The file change should be
 * back-propagated to the owner's clients (1 and 2) as well
 * as to Client3.
 */
public class ModifyWriteSharedFileIT extends BaseIT {

    /**
     * The chunk size is 1MB. Therefore, to force sending multiple
     * chunks, we have to create a string longer than 1024*1024 / 2,
     * since a char is 2 bytes long
     */
    protected static byte[] FILE_CONTENT;
    protected static byte[] CHANGED_FILE_CONTENT;

    protected static final Path TEST_DIR    = Paths.get("myDir");
    protected static final Path TEST_FILE_1 = TEST_DIR.resolve(Paths.get("myFile.txt"));
    protected static final Path TEST_FILE_2 = TEST_DIR.resolve(Paths.get("myFile2.txt"));

    protected static final Path WRITE_SHARED_TEST_DIR    = Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_DIR);
    protected static final Path WRITE_SHARED_TEST_FILE_1 = Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_1);
    protected static final Path WRITE_SHARED_TEST_FILE_2 = Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(TEST_FILE_2);

    protected static final UUID FILE_ID_TEST_DIR    = UUID.randomUUID();
    protected static final UUID FILE_ID_TEST_FILE_1 = UUID.randomUUID();
    protected static final UUID FILE_ID_TEST_FILE_2 = UUID.randomUUID();

    @BeforeClass
    public static void setUpChildIT()
            throws IOException, InterruptedException, InputOutputException {
        FILE_CONTENT = createFileContent();

        // client1
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR));
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_1), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(ROOT_TEST_DIR1.resolve(TEST_FILE_2), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // client2
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR));
        Files.write(ROOT_TEST_DIR2.resolve(TEST_FILE_1), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(ROOT_TEST_DIR2.resolve(TEST_FILE_2), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // client3
        Files.createDirectory(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_DIR));
        Files.write(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_FILE_1), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_FILE_2), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // client4
        Files.createDirectory(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_DIR));
        Files.write(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_1), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.write(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_2), FILE_CONTENT, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // apply changes
        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());
        OBJECT_STORE_3.sync(ROOT_TEST_DIR3.toFile());
        OBJECT_STORE_4.sync(ROOT_TEST_DIR4.toFile());

        // share the files
        OBJECT_STORE_1.getSharerManager().addOwner(USER_1.getUserName(), TEST_DIR.toString());
        OBJECT_STORE_1.getSharerManager().addOwner(USER_1.getUserName(), TEST_FILE_1.toString());
        OBJECT_STORE_1.getSharerManager().addOwner(USER_1.getUserName(), TEST_FILE_2.toString());
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_DIR.toString());
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_FILE_1.toString());
        OBJECT_STORE_1.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_FILE_2.toString());

        OBJECT_STORE_2.getSharerManager().addOwner(USER_1.getUserName(), TEST_DIR.toString());
        OBJECT_STORE_2.getSharerManager().addOwner(USER_1.getUserName(), TEST_FILE_1.toString());
        OBJECT_STORE_2.getSharerManager().addOwner(USER_1.getUserName(), TEST_FILE_2.toString());
        OBJECT_STORE_2.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_DIR.toString());
        OBJECT_STORE_2.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_FILE_1.toString());
        OBJECT_STORE_2.getSharerManager().addSharer(USER_2.getUserName(), AccessType.WRITE, TEST_FILE_2.toString());

        OBJECT_STORE_3.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_DIR.toString());
        OBJECT_STORE_3.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_FILE_1.toString());
        OBJECT_STORE_3.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_FILE_2.toString());
        // no need to add sharer here since user2 did not share the file with anyone
        // instead we have to set the access type
        PathObject testDirClient3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_DIR.toString());
        testDirClient3.setAccessType(AccessType.WRITE);
        OBJECT_STORE_3.getObjectManager().writeObject(testDirClient3);
        PathObject testFile1Client3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        testFile1Client3.setAccessType(AccessType.WRITE);
        OBJECT_STORE_3.getObjectManager().writeObject(testFile1Client3);
        PathObject testFile2Client3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_2.toString());
        testFile2Client3.setAccessType(AccessType.WRITE);
        OBJECT_STORE_3.getObjectManager().writeObject(testFile2Client3);


        OBJECT_STORE_4.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_DIR.toString());
        OBJECT_STORE_4.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_FILE_1.toString());
        OBJECT_STORE_4.getSharerManager().addOwner(USER_1.getUserName(), WRITE_SHARED_TEST_FILE_2.toString());
        // no need to add sharer here since user2 did not share the file with anyone
        // instead we have to set the access type
        PathObject testDirClient4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_DIR.toString());
        testDirClient4.setAccessType(AccessType.WRITE);
        OBJECT_STORE_4.getObjectManager().writeObject(testDirClient4);
        PathObject testFile1Client4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        testFile1Client4.setAccessType(AccessType.WRITE);
        OBJECT_STORE_4.getObjectManager().writeObject(testFile1Client4);
        PathObject testFile2Client4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_2.toString());
        testFile2Client4.setAccessType(AccessType.WRITE);
        OBJECT_STORE_4.getObjectManager().writeObject(testFile2Client4);

        // setup the changed file content
        CHANGED_FILE_CONTENT = Arrays.copyOf(FILE_CONTENT, FILE_CONTENT.length - 100);

        EVENT_AGGREGATOR_1.start();
        EVENT_AGGREGATOR_2.start();
        EVENT_AGGREGATOR_3.start();
        EVENT_AGGREGATOR_4.start();

        // wait a little to allow event aggregators to start
        Thread.sleep(100L);

        // check whether the file exists on client1
        assertTrue("Test dir should exist on client1", Files.exists(ROOT_TEST_DIR1.resolve(TEST_DIR)));
        assertTrue("Test file1 should exist on client1", Files.exists(ROOT_TEST_DIR1.resolve(TEST_FILE_1)));
        assertTrue("Test file2 should exist on client1", Files.exists(ROOT_TEST_DIR1.resolve(TEST_FILE_2)));

        PathObject testDirClient1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR.toString());
        PathObject testFile1Client1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_FILE_1.toString());
        PathObject testFile2Client1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_FILE_2.toString());

        assertNotNull("PathObject for testDirClient1 should not be null", testDirClient1);
        assertNotNull("PathObject for testFile1Client1 should not be null", testFile1Client1);
        assertNotNull("PathObject for testFile2Client1 should not be null", testFile2Client1);

        assertTrue("TestDirClient1 should be shared", testDirClient1.isShared());
        assertTrue("TestFile1Client1 should be shared", testFile1Client1.isShared());
        assertTrue("TestFile2Client1 should be shared", testFile2Client1.isShared());

        assertEquals("Owner should be set to own user", USER_1.getUserName(), testDirClient1.getOwner());
        assertEquals("Owner should be set to own user", USER_1.getUserName(), testFile1Client1.getOwner());
        assertEquals("Owner should be set to own user", USER_1.getUserName(), testFile2Client1.getOwner());

        assertEquals("Sharer should be user2", USER_2.getUserName(), testDirClient1.getSharers().iterator().next().getUsername());
        assertEquals("Sharer should be user2", USER_2.getUserName(), testFile1Client1.getSharers().iterator().next().getUsername());
        assertEquals("Sharer should be user2", USER_2.getUserName(), testFile2Client1.getSharers().iterator().next().getUsername());

        // client 2
        assertTrue("Test dir should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR)));
        assertTrue("Test file1 should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_1)));
        assertTrue("Test file2 should exist on client2", Files.exists(ROOT_TEST_DIR2.resolve(TEST_FILE_2)));

        PathObject testDirClient2 = OBJECT_STORE_2.getObjectManager().getObjectForPath(TEST_DIR.toString());
        PathObject testFile1Client2 = OBJECT_STORE_2.getObjectManager().getObjectForPath(TEST_FILE_1.toString());
        PathObject testFile2Client2 = OBJECT_STORE_2.getObjectManager().getObjectForPath(TEST_FILE_2.toString());

        assertNotNull("PathObject for testDirClient2 should not be null", testDirClient2);
        assertNotNull("PathObject for testFile1Client2 should not be null", testFile1Client2);
        assertNotNull("PathObject for testFile2Client2 should not be null", testFile2Client2);

        assertTrue("TestDirClient2 should be shared", testDirClient2.isShared());
        assertTrue("TestFile1Client2 should be shared", testFile1Client2.isShared());
        assertTrue("TestFile2Client2 should be shared", testFile2Client2.isShared());

        assertEquals("Owner should be set to own user", USER_1.getUserName(), testDirClient2.getOwner());
        assertEquals("Owner should be set to own user", USER_1.getUserName(), testFile1Client2.getOwner());
        assertEquals("Owner should be set to own user", USER_1.getUserName(), testFile2Client2.getOwner());

        assertEquals("Sharer should be user2", USER_2.getUserName(), testDirClient2.getSharers().iterator().next().getUsername());
        assertEquals("Sharer should be user2", USER_2.getUserName(), testFile1Client2.getSharers().iterator().next().getUsername());
        assertEquals("Sharer should be user2", USER_2.getUserName(), testFile2Client2.getSharers().iterator().next().getUsername());

        // client 3
        assertTrue("Test dir should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_DIR)));
        assertTrue("Test file1 should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_FILE_1)));
        assertTrue("Test file2 should exist on client3", Files.exists(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_FILE_2)));

        testDirClient3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_DIR.toString());
        testFile1Client3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        testFile2Client3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_2.toString());

        assertNotNull("PathObject for testDirClient3 should not be null", testDirClient3);
        assertNotNull("PathObject for testFile1Client3 should not be null", testFile1Client3);
        assertNotNull("PathObject for testFile2Client3 should not be null", testFile2Client3);

        assertFalse("TestDirClient3 should not be shared", testDirClient3.isShared());
        assertFalse("TestFile1Client3 should not be shared", testFile1Client3.isShared());
        assertFalse("TestFile2Client3 should not be shared", testFile2Client3.isShared());

        assertEquals("Owner should be set to null", USER_1.getUserName(), testDirClient3.getOwner());
        assertEquals("Owner should be set to null", USER_1.getUserName(), testFile1Client3.getOwner());
        assertEquals("Owner should be set to null", USER_1.getUserName(), testFile2Client3.getOwner());

        assertEquals("No sharer should exist", 0, testDirClient3.getSharers().size());
        assertEquals("No sharer should exist", 0, testFile1Client3.getSharers().size());
        assertEquals("No sharer should exist", 0, testFile2Client3.getSharers().size());

        // client 4
        assertTrue("Test dir should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_DIR)));
        assertTrue("Test file1 should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_1)));
        assertTrue("Test file2 should exist on client4", Files.exists(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_2)));

        testDirClient4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_DIR.toString());
        testFile1Client4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        testFile2Client4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_2.toString());

        assertNotNull("PathObject for testDirClient4 should not be null", testDirClient4);
        assertNotNull("PathObject for testFile1Client4 should not be null", testFile1Client4);
        assertNotNull("PathObject for testFile2Client4 should not be null", testFile2Client4);

        assertFalse("TestDirClient4 should not be shared", testDirClient4.isShared());
        assertFalse("TestFile1Client4 should not be shared", testFile1Client4.isShared());
        assertFalse("TestFile2Client4 should not be shared", testFile2Client4.isShared());

        assertEquals("Owner should be set to null", USER_1.getUserName(), testDirClient4.getOwner());
        assertEquals("Owner should be set to null", USER_1.getUserName(), testFile1Client4.getOwner());
        assertEquals("Owner should be set to null", USER_1.getUserName(), testFile2Client4.getOwner());

        assertEquals("No sharer should exist", 0, testDirClient4.getSharers().size());
        assertEquals("No sharer should exist", 0, testFile1Client4.getSharers().size());
        assertEquals("No sharer should exist", 0, testFile2Client4.getSharers().size());

        // insert the file identifiers on both users
        CLIENT_1.getIdentifierManager().addIdentifier(TEST_DIR.toString(), FILE_ID_TEST_DIR);
        CLIENT_1.getIdentifierManager().addIdentifier(TEST_FILE_1.toString(), FILE_ID_TEST_FILE_1);
        CLIENT_1.getIdentifierManager().addIdentifier(TEST_FILE_2.toString(), FILE_ID_TEST_FILE_2);

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_1.getIdentifierManager().getValue(TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_1.getIdentifierManager().getValue(TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_1.getIdentifierManager().getValue(TEST_FILE_2.toString()));

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_2.getIdentifierManager().getValue(TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_2.getIdentifierManager().getValue(TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_2.getIdentifierManager().getValue(TEST_FILE_2.toString()));

        CLIENT_3.getIdentifierManager().addIdentifier(WRITE_SHARED_TEST_DIR.toString(), FILE_ID_TEST_DIR);
        CLIENT_3.getIdentifierManager().addIdentifier(WRITE_SHARED_TEST_FILE_1.toString(), FILE_ID_TEST_FILE_1);
        CLIENT_3.getIdentifierManager().addIdentifier(WRITE_SHARED_TEST_FILE_2.toString(), FILE_ID_TEST_FILE_2);

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_2.toString()));

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_2.toString()));

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
    public void testWriteOnSharerSide()
            throws InputOutputException, IOException, InterruptedException {
        // modify one file on client4
        Files.write(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_1), CHANGED_FILE_CONTENT, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        // wait until change is back-propagated to client 3, client 2 and client 1
        Thread.sleep(120000L);

        byte[] contentClient4 = Files.readAllBytes(ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_FILE_1));
        byte[] contentClient3 = Files.readAllBytes(ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_FILE_1));
        byte[] contentClient2 = Files.readAllBytes(ROOT_TEST_DIR2.resolve(TEST_FILE_1));
        byte[] contentClient1 = Files.readAllBytes(ROOT_TEST_DIR1.resolve(TEST_FILE_1));

        assertArrayEquals("Content should be equal", CHANGED_FILE_CONTENT, contentClient4);
        assertArrayEquals("Content should be equal", CHANGED_FILE_CONTENT, contentClient3);
        assertArrayEquals("Content should be equal", CHANGED_FILE_CONTENT, contentClient2);
        assertArrayEquals("Content should be equal", CHANGED_FILE_CONTENT, contentClient1);

        // check that a new version is inserted
        PathObject testFile1Client4 = OBJECT_STORE_4.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        PathObject testFile1Client3 = OBJECT_STORE_3.getObjectManager().getObjectForPath(WRITE_SHARED_TEST_FILE_1.toString());
        PathObject testFile1Client2 = OBJECT_STORE_2.getObjectManager().getObjectForPath(TEST_FILE_1.toString());
        PathObject testFile1Client1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_FILE_1.toString());

        assertEquals("Version history size should be 2", 2, testFile1Client4.getVersions().size());
        assertEquals("Version history size should be 2", 2, testFile1Client3.getVersions().size());
        assertEquals("Version history size should be 2", 2, testFile1Client2.getVersions().size());
        assertEquals("Version history size should be 2", 2, testFile1Client1.getVersions().size());

        // assert that no conflict file was created
        assertEquals("Only two files should be contained in the test dir", 2, ROOT_TEST_DIR4.resolve(WRITE_SHARED_TEST_DIR).toFile().listFiles().length);
        assertEquals("Only two files should be contained in the test dir", 2, ROOT_TEST_DIR3.resolve(WRITE_SHARED_TEST_DIR).toFile().listFiles().length);
        assertEquals("Only two files should be contained in the test dir", 2, ROOT_TEST_DIR2.resolve(TEST_DIR).toFile().listFiles().length);
        assertEquals("Only two files should be contained in the test dir", 2, ROOT_TEST_DIR1.resolve(TEST_DIR).toFile().listFiles().length);

        // assert that file identifiers are still present
        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_1.getIdentifierManager().getValue(TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_1.getIdentifierManager().getValue(TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_1.getIdentifierManager().getValue(TEST_FILE_2.toString()));

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_2.getIdentifierManager().getValue(TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_2.getIdentifierManager().getValue(TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_2.getIdentifierManager().getValue(TEST_FILE_2.toString()));

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_3.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_2.toString()));

        assertEquals("Identifier should be present", FILE_ID_TEST_DIR, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_DIR.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_1, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_1.toString()));
        assertEquals("Identifier should be present", FILE_ID_TEST_FILE_2, CLIENT_4.getIdentifierManager().getValue(WRITE_SHARED_TEST_FILE_2.toString()));
    }
}
