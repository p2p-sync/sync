package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FilePushRequest implements IRequest {

    protected UUID exchangeId;
    protected ClientDevice clientDevice;

    protected String relativeFilePath;
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
     * The chunk size used for the whole transport of the file. In Bytes.
     */
    protected int chunkSize;

    protected List<ClientLocation> receiverAddresses;

    public FilePushRequest(UUID exchangeId, ClientDevice clientDevice, String relativeFilePath, long chunkCounter, int chunkSize, long totalNrOfChunks, long totalFileSize, Data data, ClientLocation receiverAddress) {
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.relativeFilePath = relativeFilePath;
        this.receiverAddresses = new ArrayList<>();
        this.receiverAddresses.add(receiverAddress);
        this.chunkCounter = chunkCounter;
        this.chunkSize = chunkSize;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;
    }

    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return this.receiverAddresses;
    }


    @Override
    public UUID getExchangeId() {
        return this.exchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    /**
     * Returns the relative file path of the file for which this chunk is for
     *
     * @return The relative file path to the file
     */
    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    /**
     * Returns the counter for the chunk which is hold by this request
     * @return The chunk counter
     */
    public long getChunkCounter() {
        return chunkCounter;
    }

    /**
     * Returns the total number of chunks which have to be fetched
     * to get the complete file represented by the file on the path
     * returned by {@link FilePushRequest#getRelativeFilePath()}
     *
     * @return The total number of chunks
     */
    public long getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    /**
     * Returns the total file size of the file once all
     * chunks have been combined.
     *
     * @return The total file size in bytes
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * Returns the actual chunk
     *
     * @return The actual chunk
     */
    public Data getData() {
        return data;
    }

    /**
     * Returns size of chunks.
     * This <b style="color:red">must</b> not be changed during a file exchange process.
     *
     * @return The chunk size in bytes
     */
    public int getChunkSize() {
        return chunkSize;
    }
}
