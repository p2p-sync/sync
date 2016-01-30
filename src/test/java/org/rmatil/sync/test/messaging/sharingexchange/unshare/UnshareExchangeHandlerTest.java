package org.rmatil.sync.test.messaging.sharingexchange.unshare;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareExchangeHandlerResult;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.network.core.model.ClientLocation;
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

public class UnshareExchangeHandlerTest extends BaseNetworkHandlerTest {

    protected static Path TEST_DIR_1  = Paths.get("testDir1");
    protected static UUID EXCHANGE_ID = UUID.randomUUID();
    protected static UUID FILE_ID     = UUID.randomUUID();

    @BeforeClass
    public static void setUpChild()
            throws IOException, InterruptedException, InputOutputException {
        CLIENT_2.shutdown();

        // wait a bit until client2 has correctly shutdown
        Thread.sleep(1000L);

        CLIENT_2 = createClient(USER_2, STORAGE_ADAPTER_2, OBJECT_STORE_2, GLOBAL_EVENT_BUS_2, PORT_CLIENT_2, new RemoteClientLocation(
                CLIENT_1.getPeerAddress().inetAddress().getHostName(),
                CLIENT_1.getPeerAddress().isIPv6(),
                CLIENT_1.getPeerAddress().tcpPort()
        ));

        CLIENT_MANAGER_2 = CLIENT_2.getClientManager();

        Files.createDirectory(ROOT_TEST_DIR1.resolve(TEST_DIR_1));
        Files.createDirectory(ROOT_TEST_DIR2.resolve(TEST_DIR_1));

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());

        Sharer sharer = new Sharer(
                USERNAME,
                AccessType.WRITE,
                new ArrayList<>()
        );

        PathObject pathObject1 = OBJECT_STORE_1.getObjectManager().getObjectForPath(TEST_DIR_1.toString());
        pathObject1.getSharers().add(
                sharer
        );

        OBJECT_STORE_1.getObjectManager().writeObject(pathObject1);

        PathObject pathObject2 = OBJECT_STORE_2.getObjectManager().getObjectForPath(TEST_DIR_1.toString());
        pathObject2.getSharers().add(
                sharer
        );

        OBJECT_STORE_2.getObjectManager().writeObject(pathObject2);

        CLIENT_1.getIdentifierManager().addIdentifier(TEST_DIR_1.toString(), FILE_ID);
        CLIENT_2.getIdentifierManager().addIdentifier(TEST_DIR_1.toString(), FILE_ID);
    }

    @Test
    public void testSendFile()
            throws InterruptedException, IOException, InputOutputException {
        UnshareExchangeHandler unshareExchangeHandler = new UnshareExchangeHandler(
                CLIENT_1,
                new ClientLocation(CLIENT_2.getClientDeviceId(), CLIENT_2.getPeerAddress()),
                FILE_ID,
                EXCHANGE_ID
        );

        CLIENT_1.getObjectDataReplyHandler().addResponseCallbackHandler(EXCHANGE_ID, unshareExchangeHandler);

        Thread fileUnshareExchangeHandlerThread = new Thread(unshareExchangeHandler);
        fileUnshareExchangeHandlerThread.setName("TEST-UnshareExchangeHandler");
        fileUnshareExchangeHandlerThread.start();

        // use a max of 30000 milliseconds to wait
        unshareExchangeHandler.await();

        CLIENT_1.getObjectDataReplyHandler().removeResponseCallbackHandler(EXCHANGE_ID);

        assertTrue("UnshareExchangeHandler should be completed after awaiting", unshareExchangeHandler.isCompleted());

        UnshareExchangeHandlerResult unshareExchangeHandlerResult = unshareExchangeHandler.getResult();

        assertNotNull("Result should not be null", unshareExchangeHandlerResult);

        // check that file is removed on other client
        assertFalse("File should not exist anymore", Files.exists(ROOT_TEST_DIR2.resolve(TEST_DIR_1)));

        // no need to check for unshare on client2, since the file is deleted anyway
        // no need to check on client1 too, since there, the unshare is made in the unsharedExchangeHandler
    }
}
