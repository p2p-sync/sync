package org.rmatil.sync.test.messaging.sharingexchange.unshare;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class UnshareRequestTest extends BaseMessageTest {

    protected static final UUID           FILE_ID          = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        List<NodeLocation> receiver = new ArrayList<>();
        receiver.add(RECEIVER_ADDRESS);

        UnshareRequest response = new UnshareRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                receiver,
                FILE_ID
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, response.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, response.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, response.getClientDevice());
        assertEquals("ReceiverAddress is not equal", receiver, response.getReceiverAddresses());
        assertEquals("FileId sis not equal", FILE_ID, response.getFileId());
    }
}
