package org.rmatil.sync.test.messaging.fileexchange.delete;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileDeleteResponseTest {

    protected static final UUID           EXCHANGE_ID      = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);
    protected static final boolean        HAS_ACCEPTED     = true;

    @Test
    public void test() {
        FileDeleteResponse fileDeleteRequest = new FileDeleteResponse(
                EXCHANGE_ID,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS,
                HAS_ACCEPTED
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileDeleteRequest.getExchangeId());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileDeleteRequest.getClientDevice());
        assertEquals("Receivers are not equal", RECEIVER_ADDRESS, fileDeleteRequest.getReceiverAddress());
        assertEquals("Accepted should be true", HAS_ACCEPTED, fileDeleteRequest.hasAccepted());
    }
}
