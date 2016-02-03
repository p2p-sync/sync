package org.rmatil.sync.core.messaging.chunk;

import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.Set;

/**
 * A class wrapping all necessary information
 * about a particular file part, i.e. a chunk.
 */
public class Chunk {

    /**
     * The checksum of the whole file
     */
    private final String checksum;

    /**
     * The owner of the file
     */
    private final String owner;

    /**
     * The set of sharers of the file
     */
    private final Set<Sharer> sharers;

    /**
     * Whether the path is a file
     */
    private final boolean isFile;

    /**
     * The access type
     */
    private final AccessType accessType;

    /**
     * The chunk counter relative to the total number of chunks
     */
    private final long chunkCounter;

    /**
     * The total number of chunks to fetch the whole file
     */
    private final int totalNrOfChunks;

    /**
     * The total file size in bytes
     */
    private final long totalFileSize;

    /**
     * The actual chunk data
     */
    private final Data data;

    /**
     * @param checksum        The checksum of the whole file
     * @param owner           The owner of the file
     * @param sharers         The set of sharers of the file
     * @param isFile          Whether the path is a file
     * @param accessType      The access type
     * @param chunkCounter    The chunk counter relative to the total number of chunks
     * @param totalNrOfChunks The total number of chunks to fetch the whole file
     * @param totalFileSize   The total file size in bytes
     * @param data            The actual chunk data
     */
    public Chunk(String checksum, String owner, Set<Sharer> sharers, boolean isFile, AccessType accessType, long chunkCounter, int totalNrOfChunks, long totalFileSize, Data data) {
        this.checksum = checksum;
        this.owner = owner;
        this.sharers = sharers;
        this.isFile = isFile;
        this.accessType = accessType;
        this.chunkCounter = chunkCounter;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;
    }

    /**
     * Returns the checksum of the whole file
     *
     * @return The checksum
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Returns the owner of the file
     *
     * @return The owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Returns the set of sharers of this file
     *
     * @return The set of sharers
     */
    public Set<Sharer> getSharers() {
        return sharers;
    }

    /**
     * Whether the file is a file or a directory
     *
     * @return
     */
    public boolean isFile() {
        return isFile;
    }

    /**
     * The access type to this file
     *
     * @return The access type
     */
    public AccessType getAccessType() {
        return accessType;
    }

    /**
     * Returns the chunk counter relative to the total number of
     * chunks to fetch the whole file. Starts at 0
     *
     * @return The chunk counter
     */
    public long getChunkCounter() {
        return chunkCounter;
    }

    /**
     * Returns the total number of chunks to fetch the whole file
     *
     * @return The total number of chunks
     */
    public int getTotalNrOfChunks() {
        return totalNrOfChunks;
    }

    /**
     * Returns the total file size in bytes
     *
     * @return The total file size in bytes
     */
    public long getTotalFileSize() {
        return totalFileSize;
    }

    /**
     * Returns the actual chunk data
     *
     * @return The actual chunk data
     */
    public Data getData() {
        return data;
    }
}
