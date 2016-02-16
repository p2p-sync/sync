package org.rmatil.sync.test.messaging.fileexchange.delete;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileDeleteResponseTest extends BaseMessageTest {

    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        FileDeleteResponse fileDeleteRequest = new FileDeleteResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileDeleteRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileDeleteRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileDeleteRequest.getClientDevice());
        assertEquals("Receivers are not equal", RECEIVER_ADDRESS, fileDeleteRequest.getReceiverAddress());
    }
}
