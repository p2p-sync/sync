package org.rmatil.sync.test.messaging.fileexchange.delete;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileDeleteRequestTest extends BaseMessageTest {

    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);
    protected static final String         PATH_TO_DELETE   = "./path/to/delete.txt";


    @Test
    public void test() {
        List<ClientLocation> receivers = new ArrayList<>();
        receivers.add(RECEIVER_ADDRESS);

        FileDeleteRequest fileDeleteRequest = new FileDeleteRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                receivers,
                PATH_TO_DELETE
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileDeleteRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileDeleteRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileDeleteRequest.getClientDevice());
        assertEquals("Receivers are not equal", receivers, fileDeleteRequest.getReceiverAddresses());
        assertEquals("Path is not equal", PATH_TO_DELETE, fileDeleteRequest.getPathToDelete());
    }
}
