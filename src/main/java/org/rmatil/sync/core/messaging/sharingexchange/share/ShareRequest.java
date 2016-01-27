package org.rmatil.sync.core.messaging.sharingexchange.share;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.version.api.AccessType;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A request containing the first chunk of a shared file
 */
public class ShareRequest extends ARequest {

    /**
     * The file id negotiated previously
     */
    protected UUID fileId;

    /**
     * The access type which should be granted to the receiving client
     */
    protected AccessType accessType;

    /**
     * The relative path in the shared folder.
     * Useful particularly, if there is a parent folder which is also
     * shared with the receiving client.
     */
    protected String relativePathToSharedFolder;

    /**
     * Whether the path is a file
     */
    protected boolean isFile;

    /**
     * The chunk counter of this file exchange.
     * Starting at 0
     */
    protected long chunkCounter;

    /**
     * The total number of chunks to fetch
     * until the whole file is downloaded
     */
    protected long totalNrOfChunks;

    /**
     * The total file size in bytes
     */
    protected long totalFileSize;

    /**
     * The actual chunk data
     */
    protected Data data;

    /**
     * The chunk size of the file exchange.
     * MUST stay the same over the whole exchange
     */
    protected int chunkSize;

    /**
     * @param exchangeId                 The exchange id
     * @param clientDevice               The client device sending this request
     * @param receiverAddress            The receiver of this request, i.e. the sharer
     * @param fileId                     The previously negotiated file id
     * @param accessType                 The access type which should be granted to the sharer
     * @param relativePathToSharedFolder The relative path to the folder / file which is actually shared (if it is the same, the relative path is "")
     * @param isFile                     Whether the path is a file
     * @param chunkCounter               The chunk counter, starting at 0 initially
     * @param totalNrOfChunks            The total number of chunks which have to be fetched for the whole file
     * @param totalFileSize              The total file size in bytes
     * @param data                       The actual chunk data
     * @param chunkSize                  The chunk size of the file exchange. MUST stay the same over the whole file exchange
     */
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

    /**
     * Returns the file id
     *
     * @return The previously negotiated file id
     */
    public UUID getFileId() {
        return fileId;
    }

    /**
     * The access type which is granted to the sharer
     *
     * @return The access type which is granted to the sharer
     */
    public AccessType getAccessType() {
        return accessType;
    }

    /**
     * The relative path to the folder / file which is actually shared (if it is the same, the relative path is "")
     *
     * @return The relative path
     */
    public String getRelativePathToSharedFolder() {
        return relativePathToSharedFolder;
    }

    /**
     * Whether the path is a file
     *
     * @return True, if it's a file
     */
    public boolean isFile() {
        return isFile;
    }

    /**
     * The chunk counter
     *
     * @return The counter of the sent chunk
     */
    public long getChunkCounter() {
        return chunkCounter;
    }

    /**
     * The total number of chunks to fetch for the whole file
     *
     * @return The total number of chunks to fetch
     */
    public long getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    /**
     * The total file size for the whole file in bytes
     *
     * @return The file size in bytes
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * The actual chunk data
     *
     * @return The chunk
     */
    public Data getData() {
        return data;
    }

    /**
     * The chunk size in bytes. MUST stay the same
     * over the whole file exchange
     *
     * @return The actual chunk size
     */
    public int getChunkSize() {
        return chunkSize;
    }
}
