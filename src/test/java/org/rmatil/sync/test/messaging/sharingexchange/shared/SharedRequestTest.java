package org.rmatil.sync.test.messaging.sharingexchange.shared;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;
import org.rmatil.sync.version.api.AccessType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class SharedRequestTest extends BaseMessageTest {

    protected static final ClientDevice CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String       USERNAME         = "John Doe";
    protected static final String       PATH             = "myFile.txt";
    protected static final AccessType   ACCESS_TYPE      = AccessType.WRITE;


    @Test
    public void test() {
        List<NodeLocation> receiver = new ArrayList<>();
        receiver.add(RECEIVER_ADDRESS);

        SharedRequest request = new SharedRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                receiver,
                USERNAME,
                ACCESS_TYPE,
                PATH
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, request.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, request.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, request.getClientDevice());
        assertEquals("ReceiverAddress is not equal", receiver, request.getReceiverAddresses());
        assertEquals("Username is not equal", USERNAME, request.getSharer());
        assertEquals("AccessType is not equal", ACCESS_TYPE, request.getAccessType());
        assertEquals("Path is not equal", PATH, request.getRelativePath());
    }
}
