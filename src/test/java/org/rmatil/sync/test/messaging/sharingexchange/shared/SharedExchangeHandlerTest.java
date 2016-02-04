package org.rmatil.sync.test.messaging.sharingexchange.shared;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedExchangeHandler;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class SharedExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static Path TEST_DIR_2  = Paths.get("testDir2");
    protected static UUID EXCHANGE_ID = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InterruptedException, InputOutputException {

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        // only create files on first client
        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_2));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_2));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());
    }

    @Test
    public void test()
            throws InterruptedException, InputOutputException {

        SharedExchangeHandler sharedExchangeHandler = new SharedExchangeHandler(
                CLIENT_1,
                CLIENT_MANAGER_1,
                OBJECT_STORE_1,
                USERNAME_2,
                AccessType.READ,
                TEST_DIR_1.toString(),
                EXCHANGE_ID
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, sharedExchangeHandler);

        Thread sharedExchangeHandlerThread = new Thread(sharedExchangeHandler);
        sharedExchangeHandlerThread.setName("TEST-SharedExchangeHandler");
        sharedExchangeHandlerThread.start();

        sharedExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("SharedExchangeHandler should be completed after awaiting", sharedExchangeHandler.isCompleted());

        assertNotNull("SharedExchangeHandlerResult should not be null", sharedExchangeHandler.getResult());
        assertTrue("All clients should have accepted the sharedRequest", sharedExchangeHandler.getResult().hasAccepted());

        PathObject sharedObject1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR_1.toString());

        assertTrue("SharedObject1 should be shared", sharedObject1.isShared());
        assertEquals("One sharer should exist", 1, sharedObject1.getSharers().size());

        Sharer sharer = sharedObject1.getSharers().iterator().next();

        assertEquals("Sharer has different name", USER_2.getUserName(), sharer.getUsername());
        assertEquals("SharingHistory should have 1 entry", 1, sharer.getSharingHistory().size());
        assertEquals("AccessType should be read", AccessType.READ, sharer.getAccessType());


        // ok, check also client2

        PathObject sharedObject2 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR_1.toString());

        assertTrue("SharedObject1 should be shared", sharedObject2.isShared());
        assertEquals("One sharer should exist", 1, sharedObject2.getSharers().size());

        Sharer sharer2 = sharedObject2.getSharers().iterator().next();

        assertEquals("Sharer has different name", USER_2.getUserName(), sharer2.getUsername());
        assertEquals("SharingHistory should have 1 entry", 1, sharer2.getSharingHistory().size());
        assertEquals("AccessType should be read", AccessType.READ, sharer2.getAccessType());
    }
}
