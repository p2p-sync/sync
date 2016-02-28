package org.rmatil.sync.test.messaging.fileexchange.demand;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileDemandRequestTest extends BaseMessageTest {

    protected static final ClientDevice CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final NodeLocation RECEIVER_ADDRESS = new NodeLocation("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String       PATH_TO_FETCH    = "./path/to/delete.txt";
    protected static final long         CHUNK_COUNTER    = 12L;

    @Test
    public void test() {
        List<NodeLocation> receivers = new ArrayList<>();
        receivers.add(RECEIVER_ADDRESS);

        FileDemandRequest fileDeleteRequest = new FileDemandRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                PATH_TO_FETCH,
                receivers,
                CHUNK_COUNTER
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileDeleteRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileDeleteRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileDeleteRequest.getClientDevice());
        assertEquals("Receivers are not equal", receivers, fileDeleteRequest.getReceiverAddresses());
        assertEquals("Path is not equal", PATH_TO_FETCH, fileDeleteRequest.getRelativeFilePath());
        assertEquals("Chunkcounter is not equal", CHUNK_COUNTER, fileDeleteRequest.getChunkCounter());
    }
}
