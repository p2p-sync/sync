package org.rmatil.sync.test.messaging.sharingexchange.unshare;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class UnshareResponseTest extends BaseMessageTest {

    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        UnshareResponse response = new UnshareResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, response.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, response.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, response.getClientDevice());
        assertEquals("ReceiverAddress is not equal", RECEIVER_ADDRESS, response.getReceiverAddress());
    }
}
