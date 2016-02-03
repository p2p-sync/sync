package org.rmatil.sync.test.messaging.fileexchange.demand;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.test.base.BaseMessageTest;

import java.util.HashSet;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FileDemandResponseTest extends BaseMessageTest {

    protected static final ClientDevice   CLIENT_DEVICE      = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String         CHECKSUM           = "someHash";
    protected static final String         RELATIVE_FILE_PATH = "path/to/some/file.txt";
    protected static final boolean        IS_FILE            = true;
    protected static final long           CHUNK_COUNTER      = 0;
    protected static final int            CHUNK_SIZE         = 1024; // bytes
    protected static final long           TOTAL_NR_OF_CHUNKS = 1;
    protected static final long           TOTAL_FILE_SIZE    = 0;
    protected static final Data           DATA               = new Data(new byte[0], false);
    protected static final ClientLocation RECEIVER_ADDRESS   = new ClientLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        FileDemandResponse filePushRequest = new FileDemandResponse(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                CHECKSUM,
                RELATIVE_FILE_PATH,
                IS_FILE,
                CHUNK_COUNTER,
                CHUNK_SIZE,
                TOTAL_NR_OF_CHUNKS,
                TOTAL_FILE_SIZE,
                DATA,
                RECEIVER_ADDRESS,
                new HashSet<>()
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, filePushRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, filePushRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, filePushRequest.getClientDevice());
        assertEquals("Checksum is not equal", CHECKSUM, filePushRequest.getChecksum());
        assertEquals("RelativeFilePath is not equal", RELATIVE_FILE_PATH, filePushRequest.getRelativeFilePath());
        assertEquals("Is File is not equal", IS_FILE, filePushRequest.isFile());
        assertEquals("ChunkCounter is not equal", CHUNK_COUNTER, filePushRequest.getChunkCounter());
        assertEquals("ChunkSize is not equal", CHUNK_SIZE, filePushRequest.getChunkSize());
        assertEquals("TotalNrOfChunks is not equal", TOTAL_NR_OF_CHUNKS, filePushRequest.getTotalNrOfChunks());
        assertEquals("TotalFileSize is not equal", TOTAL_FILE_SIZE, filePushRequest.getTotalFileSize());
        assertEquals("Data is not equal", DATA, filePushRequest.getData());
        assertEquals("Receiver addresses should contain clientLocation", RECEIVER_ADDRESS, filePushRequest.getReceiverAddress());
    }
}
