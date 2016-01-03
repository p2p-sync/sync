package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.network.core.model.Data;

import java.util.UUID;

public class FileDemandExchangeHandlerResult {

    protected UUID exchangeId;

    protected long chunkCounter;

    protected long totalNrOfChunks;

    protected Data data;

    protected long totalFileSize;

    protected int chunkSize;

    public FileDemandExchangeHandlerResult(UUID exchangeId, long chunkCounter, int chunkSize, long totalNrOfChunks, Data data, long totalFileSize) {
        this.exchangeId = exchangeId;
        this.chunkCounter = chunkCounter;
        this.chunkSize = chunkSize;
        this.totalNrOfChunks = totalNrOfChunks;
        this.data = data;
        this.totalFileSize = totalFileSize;
    }

    public UUID getExchangeId() {
        return exchangeId;
    }

    public long getChunkCounter() {
        return chunkCounter;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    public Data getData() {
        return data;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }
}
