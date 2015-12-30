package org.rmatil.sync.core.messaging.fileexchange.demand;


import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.Data;

import java.util.UUID;

/**
 * A response containing particular parts of a file
 */
public class FileDemandResponse implements IResponse {

    /**
     * The number of the chunk which is returned
     */
    protected long chunkCounter;

    /**
     * The total number of chunks which have
     * to been requested to get the complete file
     */
    protected long totalNrOfChunks;

    /**
     * The file size in bytes
     */
    protected long totalFileSize;

    /**
     * The actual data of the request
     */
    protected Data data;

    /**
     * The client device which is sending this response
     */
    protected ClientDevice clientDevice;

    /**
     * The identifier of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * @param fileExchangeId  The identifier of the file exchange
     * @param clientDevice    The client device which is requesting the file demand (i.e. this client)
     * @param chunkCounter    The chunk counter representing which chunk is sent by this response
     * @param totalNrOfChunks The total number of chunks of which the file consists
     * @param totalFileSize   The total size of the file in bytes
     * @param data            The actual chunk data
     */
    public FileDemandResponse(UUID fileExchangeId, ClientDevice clientDevice, long chunkCounter, long totalNrOfChunks, long totalFileSize, Data data) {
        this.fileExchangeId = fileExchangeId;
        this.clientDevice = clientDevice;
        this.chunkCounter = chunkCounter;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;
    }

    /**
     * The number of the chunk which is returned
     *
     * @return The chunk number returned by this response
     */
    public long getChunkCounter() {
        return chunkCounter;
    }

    /**
     * The total number of chunks of which the file consists
     *
     * @return The total number of chunks
     */
    public long getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    /**
     * The total size in bytes of the file
     *
     * @return The total size in bytes of the file
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * The actual chunk data returned by this file
     *
     * @return The chunk
     */
    public Data getData() {
        return data;
    }

    @Override
    public UUID getExchangeId() {
        return this.fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
