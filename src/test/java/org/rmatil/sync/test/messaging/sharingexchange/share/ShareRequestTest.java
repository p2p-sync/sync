package org.rmatil.sync.test.messaging.sharingexchange.share;

import org.junit.Test;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.test.base.BaseMessageTest;
import org.rmatil.sync.version.api.AccessType;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ShareRequestTest extends BaseMessageTest {

    protected final static UUID           FILE_ID                        = UUID.randomUUID();
    protected final static AccessType     ACCESS_TYPE                    = AccessType.WRITE;
    protected static final ClientDevice   CLIENT_DEVICE                  = new ClientDevice("Inverness McKenzie", UUID.randomUUID(), null);
    protected static final String         RELATIVE_PATH_TO_SHARED_FOLDER = "path/to/some/file.txt";
    protected static final boolean        IS_FILE                        = true;
    protected static final long           CHUNK_COUNTER                  = 0;
    protected static final int            CHUNK_SIZE                     = 1024; // bytes
    protected static final long           TOTAL_NR_OF_CHUNKS             = 1;
    protected static final long           TOTAL_FILE_SIZE                = 0;
    protected static final Data           DATA                           = new Data(new byte[0], false);
    protected static final ClientLocation RECEIVER_ADDRESS               = new ClientLocation(UUID.randomUUID(), null);

    @Test
    public void test() {
        ShareRequest shareRequest = new ShareRequest(
                EXCHANGE_ID,
                STATUS_CODE,
                CLIENT_DEVICE,
                RECEIVER_ADDRESS,
                FILE_ID,
                ACCESS_TYPE,
                RELATIVE_PATH_TO_SHARED_FOLDER,
                IS_FILE,
                CHUNK_COUNTER,
                TOTAL_NR_OF_CHUNKS,
                TOTAL_FILE_SIZE,
                DATA,
                CHUNK_SIZE
        );

        assertEquals("ExchangeId is not equal", EXCHANGE_ID, shareRequest.getExchangeId());
        assertEquals("StatusCode is not equal", STATUS_CODE, shareRequest.getStatusCode());
        assertEquals("ClientDevice is not equal", CLIENT_DEVICE, shareRequest.getClientDevice());
        assertEquals("RelativeFilePath is not equal", RELATIVE_PATH_TO_SHARED_FOLDER, shareRequest.getRelativePathToSharedFolder());
        assertEquals("Is File is not equal", IS_FILE, shareRequest.isFile());
        assertEquals("ChunkCounter is not equal", CHUNK_COUNTER, shareRequest.getChunkCounter());
        assertEquals("ChunkSize is not equal", CHUNK_SIZE, shareRequest.getChunkSize());
        assertEquals("TotalNrOfChunks is not equal", TOTAL_NR_OF_CHUNKS, shareRequest.getTotalNrOfChunks());
        assertEquals("TotalFileSize is not equal", TOTAL_FILE_SIZE, shareRequest.getTotalFileSize());
        assertEquals("Data is not equal", DATA, shareRequest.getData());
        assertThat("Receiver addresses should contain clientLocation", shareRequest.getReceiverAddresses(), hasItem(RECEIVER_ADDRESS));
    }
}
