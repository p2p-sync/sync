package org.rmatil.sync.test.messaging.fileexchange.move;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileMoveRequestTest extends BaseMessageTest {

    protected static final ClientDevice CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation("Inverness McKenzie", UUID.randomUUID(), null);

    @Test
    public void test() {
        FileMoveResponse fileMoveResponse = new FileMoveResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileMoveResponse.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileMoveResponse.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileMoveResponse.getClientDevice());
        assertEquals("Receiver addresses should contain nodeLocation", RECEIVER_ADDRESS, fileMoveResponse.getReceiverAddress());
    }
}
