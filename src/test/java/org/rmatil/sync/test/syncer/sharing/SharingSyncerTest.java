package org.rmatil.sync.test.syncer.sharing;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.rmatil.sync.core.exception.SharingFailedException;
import org.rmatil.sync.core.exception.UnsharingFailedException;
import org.rmatil.sync.core.syncer.sharing.SharingSyncer;
import org.rmatil.sync.core.syncer.sharing.event.ShareEvent;
import org.rmatil.sync.core.syncer.sharing.event.UnshareEvent;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SharingSyncerTest extends BaseNetworkHandlerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

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
    public void testGetClientLocationFromSharer() {
        NodeLocation nodeLocation = SHARING_SYNCER.getClientLocationFromSharer(USER_1.getUserName());

        assertNotNull("NodeLocation should not be null", nodeLocation);

        NodeLocation nodeLocation2 = SHARING_SYNCER.getClientLocationFromSharer("someNonExistingUser");
        assertNull("NodeLocation2 should be null", nodeLocation2);
    }

    @Test
    public void testShareWithOurSelf() {
        ShareEvent shareEvent = new ShareEvent(
                TEST_DIR,
                AccessType.WRITE,
                CLIENT_1.getUser().getUserName()
        );

        thrown.expect(SharingFailedException.class);
        SHARING_SYNCER.sync(shareEvent);
    }

    @Test
    public void testShareWithOurSelf2() {
        ShareEvent shareEvent = new ShareEvent(
                TEST_DIR,
                AccessType.WRITE,
                CLIENT_1.getUser().getUserName()
        );

        thrown.expect(SharingFailedException.class);
        SHARING_SYNCER.syncShareEvent(shareEvent);
    }

    @Test
    public void testUnshareWithOurSelf() {
        UnshareEvent unshareEvent = new UnshareEvent(
                TEST_DIR,
                AccessType.WRITE,
                CLIENT_1.getUser().getUserName()
        );

        thrown.expect(UnsharingFailedException.class);
        SHARING_SYNCER.sync(unshareEvent);
    }

    @Test
    public void testUnshareWithOurSelf2() {
        UnshareEvent unshareEvent = new UnshareEvent(
                TEST_DIR,
                AccessType.WRITE,
                CLIENT_1.getUser().getUserName()
        );

        thrown.expect(UnsharingFailedException.class);
        SHARING_SYNCER.syncUnshareEvent(unshareEvent);
    }
}

