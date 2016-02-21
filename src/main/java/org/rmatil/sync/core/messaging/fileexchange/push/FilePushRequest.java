package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * Send this request object to clients, to push chunks
 * of a file or a creation of a directory
 */
public class FilePushRequest extends ARequest {

    /**
     * A checksum over the content from the complete file,
     * i.e. the combination of all chunks
     * <p>
     * <i>Note</i>: Returns null, if the checksum could not have
     * been generated on the other client.
     */
    protected String checksum;

    /**
     * The relative file to the path which should be created
     * or completed with chunks
     */
    protected String relativeFilePath;

    /**
     * Whether the path represents a directory or a file
     */
    protected boolean isFile;

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
     * The actual data of the request.
     * May be null if its a directory.
     */
    protected Data data;

    /**
     * The chunk size used for the whole transport of the file. In Bytes.
     */
    protected int chunkSize;

    /**
     * The id of the file
     */
    protected UUID fileId;

    /**
     * The owner of the file, may be null, if the file is not shared
     */
    protected String owner;

    /**
     * The access type we got for the file. May be null, if
     * the file is not shared
     */
    protected AccessType accessType;

    /**
     * The set of sharers of the file
     */
    protected Set<Sharer> sharers;

    /**
     * @param exchangeId       The exchange id of the request
     * @param statusCode       The status code of the request
     * @param clientDevice     The client device which is sending this request
     * @param owner            The owner of the file. May be null if the file is not shared
     * @param checksum         The checksum of the complete file
     * @param accessType       The access type to this file. May be null if not shared
     * @param sharers          The set of sharers stored on this client
     * @param relativeFilePath The relative path to the file which should be created
     * @param isFile           Whether the path represents a file or a directory
     * @param chunkCounter     The counter of the chunk contained in this request (starts at 0)
     * @param chunkSize        The size of the chunk for the whole file exchange in bytes.
     *                         MUST stay the same for the whole file exchange, i.e. until all chunks of a file have been transferred
     * @param totalNrOfChunks  The total number of chunks to request to get the complete file
     * @param totalFileSize    The total file size of the file once all chunks have been transferred
     * @param data             The actual chunk data
     * @param receiverAddress  The receiver of this request
     */
    public FilePushRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, String checksum, UUID fileId, String owner, AccessType accessType, Set<Sharer> sharers, String relativeFilePath, boolean isFile, long chunkCounter, int chunkSize, long totalNrOfChunks, long totalFileSize, Data data, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, new ArrayList<>());
        this.checksum = checksum;
        this.fileId = fileId;
        this.owner = owner;
        this.accessType = accessType;
        this.sharers = sharers;
        this.relativeFilePath = relativeFilePath;
        this.isFile = isFile;
        this.chunkCounter = chunkCounter;
        this.chunkSize = chunkSize;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;

        super.receiverAddresses.add(receiverAddress);
    }

    /**
     * Returns the checksum of the complete file.
     * <i>Note</i>: Returns null, if the checksum could not have
     * been generated on the other client.
     *
     * @return The checksum of the complete file.
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * The file id of the file. Only present
     * if the file is shared
     *
     * @return The file id
     */
    public UUID getFileId() {
        return fileId;
    }

    /**
     * Returns the owner of this file. May be null if not shared
     *
     * @return The owner's username, or null if not shared
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the access type to this file. May be null if not shared
     *
     * @return The access type or null if not shared
     */
    public AccessType getAccessType() {
        return accessType;
    }

    /**
     * Returns the list of sharers of the file
     *
     * @return The list of sharers
     */
    public Set<Sharer> getSharers() {
        return sharers;
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
     * Whether the path represents a file or a directory
     *
     * @return True, if it's a file, false otherwise
     */
    public boolean isFile() {
        return isFile;
    }

    /**
     * Returns the counter for the chunk which is hold by this request
     *
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
     * Returns the actual chunk data. May be null
     * if the path returned by {@link FilePushRequest#getRelativeFilePath()}
     * represents a directory
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
