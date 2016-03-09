package org.rmatil.sync.core.messaging.chunk;

import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.Set;

/**
 * Provides access to chunks of a particular file
 */
public class ChunkProvider {

    /**
     * The storage adapter to access files
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store to fetch information about sharers, etc
     */
    protected IObjectStore objectStore;

    /**
     * The path element from which to fetch chunks
     */
    protected TreePathElement pathElement;

    /**
     * @param storageAdapter The storage adapter to access files
     * @param objectStore    The object store to fetch information of the files, like sharers
     * @param pathElement    The path element from which to get chunks
     */
    public ChunkProvider(ITreeStorageAdapter storageAdapter, IObjectStore objectStore, TreePathElement pathElement) {
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.pathElement = pathElement;
    }

    /**
     * Returns the chunk at the position of the file specified by
     * the combination of chunk counter and chunk size.
     *
     * @param chunkCounter The chunk counter
     * @param chunkSize    The chunk size
     *
     * @return The requested chunk
     *
     * @throws InputOutputException     If accessing the storage adapter or the object store failed
     * @throws IllegalArgumentException If a chunk counter greater than the total nr of chunks is requested
     */
    public Chunk getChunk(long chunkCounter, int chunkSize)
            throws InputOutputException, IllegalArgumentException {
        IFileMetaInfo fileMetaInfo = this.storageAdapter.getMetaInformation(this.pathElement);

        int totalNrOfChunks = 1;
        Data data = null;
        String checksum = "";
        if (fileMetaInfo.isFile()) {
            totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / (double) chunkSize);

            if (chunkCounter > totalNrOfChunks) {
                // maybe the file has changed in the mean time...
                throw new IllegalArgumentException("ChunkCounter must be smaller than the total number of chnks");
            }

            long fileChunkStartOffset = chunkCounter * chunkSize;

            // storage adapter trims requests for a too large chunk
            byte[] content = this.storageAdapter.read(pathElement, fileChunkStartOffset, chunkSize);

            data = new Data(content, false);

            checksum = this.storageAdapter.getChecksum(pathElement);
        }

        PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(pathElement.getPath());

        String owner = pathObject.getOwner();
        Set<Sharer> sharers = pathObject.getSharers();
        AccessType accessType = pathObject.getAccessType();

        return new Chunk(
                checksum,
                owner,
                sharers,
                fileMetaInfo.isFile(),
                accessType,
                chunkCounter,
                totalNrOfChunks,
                fileMetaInfo.getTotalFileSize(),
                data
        );
    }
}
