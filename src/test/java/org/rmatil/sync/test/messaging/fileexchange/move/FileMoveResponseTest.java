package org.rmatil.sync.test.messaging.fileexchange.move;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FileMoveResponseTest extends BaseMessageTest {

    protected static final ClientDevice CLIENT_DEVICE          = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String       OLD_RELATIVE_FILE_PATH = "path/to/some/file.txt";
    protected static final String       NEW_RELATIVE_FILE_PATH = "path/to/some/new/file.txt";
    protected static final boolean      IS_FILE                = true;
    protected static final NodeLocation RECEIVER_ADDRESS       = new NodeLocation("Inverness McKenzie", UUID.randomUUID(), null);


    @Test
    public void test() {
        FileMoveRequest fileMoveRequest = new FileMoveRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS,
                OLD_RELATIVE_FILE_PATH,
                NEW_RELATIVE_FILE_PATH,
                IS_FILE
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileMoveRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileMoveRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileMoveRequest.getClientDevice());
        assertEquals("OldRelativeFilePath is not equal", OLD_RELATIVE_FILE_PATH, fileMoveRequest.getOldPath());
        assertEquals("NewRelativeFilePath is not equal", NEW_RELATIVE_FILE_PATH, fileMoveRequest.getNewPath());
        assertEquals("Is File is not equal", IS_FILE, fileMoveRequest.isFile());
        assertThat("Receiver addresses should contain nodeLocation", fileMoveRequest.getReceiverAddresses(), hasItem(RECEIVER_ADDRESS));
    }
}
