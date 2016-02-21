package org.rmatil.sync.test.messaging.fileexchange.push;

import org.junit.Test;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.test.base.BaseMessageTest;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class FilePushRequestTest extends BaseMessageTest {

    protected static final ClientDevice CLIENT_DEVICE      = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String       CHECKSUM           = "checksum";
    protected static final String       RELATIVE_FILE_PATH = "path/to/some/file.txt";
    protected static final UUID         FILE_ID            = UUID.randomUUID();
    protected static final String       OWNER              = "owner";
    protected static final AccessType   ACCESS_TYPE        = AccessType.WRITE;
    protected static final boolean      IS_FILE            = true;
    protected static final long         CHUNK_COUNTER      = 0;
    protected static final int          CHUNK_SIZE         = 1024; // bytes
    protected static final long         TOTAL_NR_OF_CHUNKS = 1;
    protected static final long         TOTAL_FILE_SIZE    = 0;
    protected static final Data         DATA               = new Data(new byte[0], false);
    protected static final NodeLocation RECEIVER_ADDRESS   = new NodeLocation(UUID.randomUUID(), null);
    protected static final Set<Sharer>  SHARERS            = new HashSet<>();

    @Test
    public void test() {
        FilePushRequest filePushRequest = new FilePushRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                CHECKSUM,
                FILE_ID,
                OWNER,
                ACCESS_TYPE,
                SHARERS,
                RELATIVE_FILE_PATH,
                IS_FILE,
                CHUNK_COUNTER,
                CHUNK_SIZE,
                TOTAL_NR_OF_CHUNKS,
                TOTAL_FILE_SIZE,
                DATA,
                RECEIVER_ADDRESS
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
        assertThat("Receiver addresses should contain nodeLocation", filePushRequest.getReceiverAddresses(), hasItem(RECEIVER_ADDRESS));
        assertEquals("Owner should be equal", OWNER, filePushRequest.getOwner());
        assertEquals("AccessType should be equal", ACCESS_TYPE, filePushRequest.getAccessType());
        assertEquals("Sharers should be equal", SHARERS, filePushRequest.getSharers());
        assertEquals("FileId should be equal", FILE_ID, filePushRequest.getFileId());
    }
}
