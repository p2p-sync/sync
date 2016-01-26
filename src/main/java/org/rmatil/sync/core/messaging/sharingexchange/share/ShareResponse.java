package org.rmatil.sync.core.messaging.sharingexchange.share;

import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class ShareResponse extends AResponse {

    /**
     * The unique file id
     */
    protected UUID fileId;

    /**
     * The counter which indicates which chunk should be requested
     */
    protected long chunkCounter;

    /**
     * @param exchangeId   The identifier of the file exchange
     * @param clientDevice The client device which is requesting the file demand (i.e. this client)
     * @param fileId       The unique id of the file which should be returned
     * @param chunkCounter The chunk number which should returned in the corresponding response to this request
     */
    public ShareResponse(UUID exchangeId, ClientDevice clientDevice, UUID fileId, ClientLocation receiverAddress, long chunkCounter) {
        super(exchangeId, clientDevice, receiverAddress);
        this.fileId = fileId;
        this.chunkCounter = chunkCounter;
    }

    /**
     * Returns the unique file id of the file which should be returned
     *
     * @return The unique file id
     */
    public UUID getFileId() {
        return fileId;
    }

    /**
     * Returns the chunk index of the chunk which should be returned
     *
     * @return The chunk number to return
     */
    public long getChunkCounter() {
        return chunkCounter;
    }
}
