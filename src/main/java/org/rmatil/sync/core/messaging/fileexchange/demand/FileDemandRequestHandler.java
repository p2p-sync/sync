package org.rmatil.sync.core.messaging.fileexchange.demand;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.chunk.Chunk;
import org.rmatil.sync.core.messaging.chunk.ChunkProvider;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles file requests by answering with chunks of the requested file
 */
public class FileDemandRequestHandler implements ILocalStateRequestCallback {

    private final static Logger logger = LoggerFactory.getLogger(FileDemandRequestHandler.class);

    /**
     * The chunk size to use for the whole file exchange
     */
    public static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    /**
     * The storage adapter to access the synced folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The client to send responses
     */
    protected INode node;

    /**
     * The file demand request which have been received
     */
    protected FileDemandRequest request;

    /**
     * The global event bus to send events to
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The access manager to check for sharer's access to files
     */
    protected IAccessManager accessManager;


    @Override
    public void setStorageAdapter(ITreeStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setNode(INode INode) {
        this.node = INode;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof FileDemandRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileDemandRequest.class.getName());
        }

        this.request = (FileDemandRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Getting requested chunk " + this.request.getChunkCounter() + " for exchange " + this.request.getExchangeId());

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.READ, this.request.getRelativeFilePath())) {
                logger.warn("Failed to get requested chunk due to missing access rights on file " + this.request.getRelativeFilePath() + " for user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(this.createErrorResponse(StatusCode.ACCESS_DENIED, - 1, - 1));
                return;
            }

            TreePathElement pathElement = new TreePathElement(this.request.getRelativeFilePath());

            ChunkProvider chunkProvider = new ChunkProvider(
                    this.storageAdapter,
                    this.objectStore,
                    pathElement
            );

            Chunk chunk;
            try {
                chunk = chunkProvider.getChunk(this.request.getChunkCounter(), CHUNK_SIZE);
            } catch (InputOutputException e) {
                logger.error("Failed to read the chunk " + this.request.getChunkCounter() + " of file " + this.request.getRelativeFilePath() + " for exchange " + this.request.getExchangeId() + ". Aborting file push exchange. Message: " + e.getMessage());
                this.sendResponse(this.createErrorResponse(StatusCode.FILE_MISSING, - 1, - 1));
                return;
            } catch (IllegalArgumentException e) {
                // requested chunk does not exist anymore
                logger.info("Detected file change during push exchange " + this.request.getExchangeId() + ". Starting to push again at chunk 0");
                try {
                    chunk = chunkProvider.getChunk(0, CHUNK_SIZE);
                } catch (InputOutputException e1) {
                    logger.error("Failed to read the chunk 0 of file " + this.request.getRelativeFilePath() + " for exchange " + this.request.getExchangeId() + " after detected file change. Aborting file push exchange. Message: " + e.getMessage());
                    this.sendResponse(this.createErrorResponse(StatusCode.FILE_MISSING, - 1, - 1));
                    return;
                }
            }

            // restart the file exchange again if the other client requests a chunk we do not have
            // maybe due to a rewrite of the file content while syncing
            if (chunk.getTotalNrOfChunks() < this.request.getChunkCounter()) {
                // no chunk anymore, fileDemandExchangeHandler is then forced to check checksum -> re-download if not matching
                this.sendResponse(this.createErrorResponse(StatusCode.FILE_CHANGED, - 1, chunk.getTotalNrOfChunks()));
                return;
            }


            Set<Sharer> sharers = new HashSet<>();
            try {
                sharers = this.objectStore.getSharerManager().getSharer(this.request.getRelativeFilePath());
            } catch (InputOutputException e) {
                logger.error("Failed to read the sharers for file " + this.request.getRelativeFilePath() + ". Sending an empty sharer set. Message: " + e.getMessage());
            }

            IResponse response = new FileDemandResponse(
                    this.request.getExchangeId(),
                    StatusCode.ACCEPTED,
                    new ClientDevice(this.node.getUser().getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress()),
                    chunk.getChecksum(),
                    this.request.getRelativeFilePath(),
                    chunk.isFile(),
                    chunk.getChunkCounter(),
                    CHUNK_SIZE,
                    chunk.getTotalNrOfChunks(),
                    chunk.getTotalFileSize(),
                    chunk.getData(),
                    new NodeLocation(
                            this.request.getClientDevice().getUserName(),
                            this.request.getClientDevice().getClientDeviceId(),
                            this.request.getClientDevice().getPeerAddress()
                    ),
                    sharers
            );

            this.sendResponse(response);

        } catch (Exception e) {
            logger.error("Got exception in FileDemandRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    protected FileDemandResponse createErrorResponse(StatusCode statusCode, long chunkCounter, long totalNrOfChunks) {
        return new FileDemandResponse(
                this.request.getExchangeId(),
                statusCode,
                new ClientDevice(
                        this.node.getUser().getUserName(),
                        this.node.getClientDeviceId(),
                        this.node.getPeerAddress()
                ),
                "",
                this.request.getRelativeFilePath(),
                true,
                chunkCounter,
                CHUNK_SIZE,
                totalNrOfChunks,
                - 1,
                null,
                new NodeLocation(
                        this.request.getClientDevice().getUserName(),
                        this.request.getClientDevice().getClientDeviceId(),
                        this.request.getClientDevice().getPeerAddress()
                ),
                new HashSet<>()
        );
    }

    /**
     * Sends the given response back to the client
     *
     * @param response The response to send back
     */
    protected void sendResponse(IResponse response) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.node.sendDirect(response.getReceiverAddress(), response);
    }
}
