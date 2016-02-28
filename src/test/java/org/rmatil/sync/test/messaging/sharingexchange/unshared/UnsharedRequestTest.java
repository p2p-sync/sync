package org.rmatil.sync.test.messaging.sharingexchange.unshared;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class UnsharedRequestTest extends BaseMessageTest {

    protected static final UUID         FILE_ID          = UUID.randomUUID();
    protected static final ClientDevice CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String       USERNAME         = "John Doe";


    @Test
    public void test() {
        List<NodeLocation> receiver = new ArrayList<>();
        receiver.add(RECEIVER_ADDRESS);

        UnsharedRequest request = new UnsharedRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                receiver,
                USERNAME,
                FILE_ID
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, request.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, request.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, request.getClientDevice());
        assertEquals("ReceiverAddress is not equal", receiver, request.getReceiverAddresses());
        assertEquals("Username is not equal", USERNAME, request.getSharer());
        assertEquals("FileId is not equal", FILE_ID, request.getFileId());
    }
}
