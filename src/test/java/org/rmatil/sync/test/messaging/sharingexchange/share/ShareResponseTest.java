package org.rmatil.sync.test.messaging.sharingexchange.share;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ShareResponseTest extends BaseMessageTest {

    protected static final UUID           FILE_ID          = UUID.randomUUID();
    protected static final ClientDevice   CLIENT_DEVICE    = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final ClientLocation RECEIVER_ADDRESS = new ClientLocation(UUID.randomUUID(), null);
    protected static final long           CHUNK_COUNTER    = 12L;

    @Test
    public void test() {
        ShareResponse fileDeleteRequest = new ShareResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                FILE_ID,
                RECEIVER_ADDRESS,
                CHUNK_COUNTER
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, fileDeleteRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, fileDeleteRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, fileDeleteRequest.getClientDevice());
        assertEquals("Chunkcounter is not equal", CHUNK_COUNTER, fileDeleteRequest.getChunkCounter());
        assertEquals("FileId is not equal", FILE_ID, fileDeleteRequest.getFileId());
        assertEquals("ReceiverAddress is not equal", RECEIVER_ADDRESS, fileDeleteRequest.getReceiverAddress());
    }
}
