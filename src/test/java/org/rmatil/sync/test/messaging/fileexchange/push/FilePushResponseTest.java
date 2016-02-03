package org.rmatil.sync.test.messaging.fileexchange.push;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FilePushResponseTest extends BaseMessageTest {

    protected static final ClientDevice   CLIENT_DEVICE      = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String         RELATIVE_FILE_PATH = "path/to/some/file.txt";
    protected static final long           CHUNK_COUNTER      = 0;
    protected static final ClientLocation RECEIVER_ADDRESS   = new ClientLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        FilePushResponse filePushResponse = new FilePushResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RELATIVE_FILE_PATH,
                RECEIVER_ADDRESS,
                CHUNK_COUNTER
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, filePushResponse.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, filePushResponse.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, filePushResponse.getClientDevice());
        assertEquals("RelativeFilePath is not equal", RELATIVE_FILE_PATH, filePushResponse.getRelativeFilePath());
        assertEquals("ChunkCounter is not equal", CHUNK_COUNTER, filePushResponse.getChunkCounter());
        assertEquals("Receiver addresses should be equal", filePushResponse.getReceiverAddress(), RECEIVER_ADDRESS);
    }
}
