package org.rmatil.sync.core.messaging.fileexchange.demand;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles file requests by answering with chunks of the requested file
 */
public class FileDemandRequestHandler implements ObjectDataReply {

    private final static Logger logger = LoggerFactory.getLogger(FileDemandRequestHandler.class);

    protected ClientDevice    clientDevice;
    protected IObjectStore    objectStore;
    protected IStorageAdapter storageAdapter;
    protected int             chunkSize;

    /**
     * @param storageAdapter The storage adapter which has access to all files which should be shared
     * @param chunkSize      The chunk size used for file sharing
     */
    public FileDemandRequestHandler(ClientDevice clientDevice, IObjectStore objectStore, IStorageAdapter storageAdapter, int chunkSize) {
        this.clientDevice = clientDevice;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
        this.chunkSize = chunkSize;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        // TODO: check if null is allowed as return value

        if (! (request instanceof FileDemandRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        FileDemandRequest fileRequest = (FileDemandRequest) request;
        IPathElement pathElement = new LocalPathElement(fileRequest.getRelativeFilePath());
        IFileMetaInfo fileMetaInfo;
        try {
            fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
        } catch (InputOutputException e) {
            logger.error("Could not fetch meta information about " + pathElement.getPath() + ". Message: " + e.getMessage());
            return null;
        }

        // TODO: check access for file by comparing requesting user and shared with users in object store
        // this.objectStore.getObjectManager().getObject()

        // should round to the next bigger int value anyway
        int totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / this.chunkSize);
        int fileChunkStartOffset = fileRequest.getChunkCounter() * this.chunkSize;

        // storage adapter trims requests for a too large chunk
        byte[] content;
        try {
            content = this.storageAdapter.read(pathElement, fileChunkStartOffset, this.chunkSize);
        } catch (InputOutputException e) {
            logger.error("Could not read file contents of " + pathElement.getPath() + " at offset " + fileChunkStartOffset + " bytes with chunk size of " + this.chunkSize + " bytes");
            return null;
        }

        Data data = new Data(content, false);

        logger.info("Sending chunk " + ((FileDemandRequest) request).getChunkCounter() + " for file on path " + fileRequest.getRelativeFilePath());

        return new FileDemandResponse(
                ((FileDemandRequest) request).fileExchangeId,
                this.clientDevice,
                ((FileDemandRequest) request).getChunkCounter(),
                totalNrOfChunks,
                fileMetaInfo.getTotalFileSize(),
                data
        );
    }
}
