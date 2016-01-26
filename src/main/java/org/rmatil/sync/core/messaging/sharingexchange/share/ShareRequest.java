package org.rmatil.sync.core.messaging.sharingexchange.share;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.version.api.AccessType;

import java.util.ArrayList;
import java.util.UUID;

public class ShareRequest extends ARequest {

    protected UUID fileId;

    protected AccessType accessType;

    protected String relativePathToSharedFolder;

    protected boolean isFile;

    protected long chunkCounter;

    protected long totalNrOfChunks;

    protected long totalFileSize;

    protected Data data;

    protected int chunkSize;

    public ShareRequest(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, UUID fileId, AccessType accessType, String relativePathToSharedFolder, boolean isFile, long chunkCounter, long totalNrOfChunks, long totalFileSize, Data data, int chunkSize) {
        super(exchangeId, clientDevice, new ArrayList<>());
        this.fileId = fileId;
        this.accessType = accessType;
        this.relativePathToSharedFolder = relativePathToSharedFolder;
        this.isFile = isFile;
        this.chunkCounter = chunkCounter;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;
        this.chunkSize = chunkSize;

        super.receiverAddresses.add(receiverAddress);
    }

    public UUID getFileId() {
        return fileId;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getRelativePathToSharedFolder() {
        return relativePathToSharedFolder;
    }

    public boolean isFile() {
        return isFile;
    }

    public long getChunkCounter() {
        return chunkCounter;
    }

    public long getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    public long getTotalFileSize() {
        return totalFileSize;
    }

    public Data getData() {
        return data;
    }

    public int getChunkSize() {
        return chunkSize;
    }
}
