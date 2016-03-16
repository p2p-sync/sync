package org.rmatil.sync.test.messaging.sharingexchange.unshared;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedExchangeHandler;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.*;

public class UnsharedExchangetHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static UUID EXCHANGE_ID = UUID.randomUUID();
    protected static UUID FILE_ID     = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InterruptedException, InputOutputException {

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        OBJECT_STORE_1.sync();
        OBJECT_STORE_2.sync();

        OBJECT_STORE_1.sync();
        OBJECT_STORE_2.sync();

        Sharer sharer = new Sharer(
                USERNAME_2,
                AccessType.WRITE,
                new ArrayList<>()
        );

        OBJECT_STORE_1.getSharerManager().addSharer(
                sharer.getUsername(),
                AccessType.WRITE,
                TEST_DIR_1.toString()
        );

        OBJECT_STORE_2.getSharerManager().addSharer(
                sharer.getUsername(),
                AccessType.WRITE,
                TEST_DIR_1.toString()
        );

        CLIENT_1.getIdentifierManager().addIdentifier(TEST_DIR_1.toString(), FILE_ID);
    }

    @Test
    public void test()
            throws InterruptedException, InputOutputException {

        UnsharedExchangeHandler unsharedExchangeHandler = new UnsharedExchangeHandler(
                CLIENT_1,
                CLIENT_MANAGER_1,
                OBJECT_STORE_1,
                TEST_DIR_1.toString(),
                FILE_ID,
                USERNAME_2,
                EXCHANGE_ID
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, unsharedExchangeHandler);

        Thread sharedExchangeHandlerThread = new Thread(unsharedExchangeHandler);
        sharedExchangeHandlerThread.setName("TEST-SharedExchangeHandler");
        sharedExchangeHandlerThread.start();

        unsharedExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("UnshareExchangeHandler should be completed after awaiting", unsharedExchangeHandler.isCompleted());

        PathObject sharedObject1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR_1.toString());

        assertFalse("SharedObject1 should be unshared", sharedObject1.isShared());
        assertEquals("No sharer should exist anymore", 1, sharedObject1.getSharers().size());

        Sharer sharer = sharedObject1.getSharers().iterator().next();

        assertEquals("Sharer has different name", USER_2.getUserName(), sharer.getUsername());
        assertEquals("SharingHistory should have 2 entries", 2, sharer.getSharingHistory().size());
        assertEquals("AccessType should be removed", AccessType.ACCESS_REMOVED, sharer.getAccessType());


        // ok, check also client2

        PathObject sharedObject2 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR_1.toString());

        assertFalse("SharedObject1 should be unshared", sharedObject2.isShared());
        assertEquals("One sharer should exist", 1, sharedObject2.getSharers().size());

        Sharer sharer2 = sharedObject2.getSharers().iterator().next();

        assertEquals("Sharer has different name", USER_2.getUserName(), sharer2.getUsername());
        assertEquals("SharingHistory should have 2 entries", 2, sharer2.getSharingHistory().size());
        assertEquals("AccessType should be removed", AccessType.ACCESS_REMOVED, sharer2.getAccessType());
    }
}
