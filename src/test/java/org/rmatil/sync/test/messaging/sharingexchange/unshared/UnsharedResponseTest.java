package org.rmatil.sync.test.messaging.sharingexchange.unshared;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class UnsharedResponseTest {

    protected static final UUID           EXCHANGE_ID      = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);
    protected static final boolean        HAS_ACCEPTED     = true;

    @Test
    public void test() {
        UnshareResponse response = new UnshareResponse(
                EXCHANGE_ID,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS,
                HAS_ACCEPTED
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, response.getExchangeId());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, response.getClientDevice());
        assertEquals("ReceiverAddress is not equal", RECEIVER_ADDRESS, response.getReceiverAddress());
        assertEquals("HasAccepted is not equal", HAS_ACCEPTED, response.hasAccepted());
    }
}
