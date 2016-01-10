package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class FilePushResponse extends AResponse {

    /**
     * The relative path to the file which should be returned
     */
    protected String relativeFilePath;

    /**
     * The counter which indicates which chunk should be requested
     */
    protected long chunkCounter;

    /**
     * @param exchangeId       The identifier of the file exchange
     * @param clientDevice     The client device which is requesting the file demand (i.e. this client)
     * @param relativeFilePath The relative path to the file which should be returned
     * @param chunkCounter     The chunk number which should returned in the corresponding response to this request
     */
    public FilePushResponse(UUID exchangeId, ClientDevice clientDevice, String relativeFilePath, ClientLocation receiverAddress, long chunkCounter) {
        super(exchangeId, clientDevice, receiverAddress);
        this.relativeFilePath = relativeFilePath;
        this.chunkCounter = chunkCounter;
    }

    /**
     * Returns the relative path to the file which should be returned
     *
     * @return The relative path
     */
    public String getRelativeFilePath() {
        return relativeFilePath;
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
