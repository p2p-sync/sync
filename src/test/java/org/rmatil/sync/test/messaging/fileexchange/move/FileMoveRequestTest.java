package org.rmatil.sync.test.messaging.fileexchange.move;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileMoveRequestTest {

    protected static final UUID           EXCHANGE_ID      = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final boolean        HAS_ACCEPTED     = true;
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        FileMoveResponse fileMoveResponse = new FileMoveResponse(
                EXCHANGE_ID,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS,
                HAS_ACCEPTED
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileMoveResponse.getExchangeId());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileMoveResponse.getClientDevice());
        assertEquals("Receiver addresses should contain clientLocation", RECEIVER_ADDRESS, fileMoveResponse.getReceiverAddress());
        assertEquals("Has accepted is not equal", HAS_ACCEPTED, fileMoveResponse.hasAccepted());
    }
}
