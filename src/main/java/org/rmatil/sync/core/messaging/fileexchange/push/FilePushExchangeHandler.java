package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.chunk.Chunk;
import org.rmatil.sync.core.messaging.chunk.ChunkProvider;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FilePushExchangeHandler extends ANetworkHandler<FilePushExchangeHandlerResult> implements ILocalStateResponseCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferExchangeHandler.class);

    /**
     * Wait a maximum of 2 minutes for a file exchange to complete
     */
    protected static final long MAX_FILE_WAITNG_TIME = 120000L;

    /**
     * The chunk size to use for the whole file exchange
     */
    protected static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    /**
     * The id of the file exchange
     */
    protected UUID exchangeId;

    /**
     * A storage adapter to access the synchronized folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    /**
     * The client manager to access client locations
     */
    protected INodeManager nodeManager;

    /**
     * The object store to read the sharers from
     */
    protected IObjectStore objectStore;

    /**
     * The relative path to the file/directory which should be pushed
     */
    protected String relativeFilePath;

    /**
     * A count down latch to check if all clients have received all chunks.
     * We have to use this one instead of {@link ANetworkHandler#countDownLatch} since
     * we are sending file chunks as subrequests one by one
     */
    protected CountDownLatch chunkCountDownLatch;

    /**
     * A list of client locations which should receive file push requests
     */
    protected List<NodeLocation> receivers;

    /**
     * The chunk provider
     */
    protected ChunkProvider chunkProvider;

    /**
     * The number of clients to which the exchange was sent to
     */
    protected int clientCounter;

    /**
     * The countdown latch which is completed
     * once the list with all receivers is initialised
     */
    protected CountDownLatch initReceiverLatch;

    protected UUID   fileId;
    protected String owner;


    public FilePushExchangeHandler(UUID exchangeId, ClientDevice clientDevice, ITreeStorageAdapter storageAdapter, INodeManager nodeManager, INode client, IObjectStore objectStore, List<NodeLocation> receivers, String relativeFilePath) {
        super(client);
        this.clientDevice = clientDevice;
        this.exchangeId = exchangeId;
        this.storageAdapter = storageAdapter;
        this.nodeManager = nodeManager;
        this.objectStore = objectStore;
        this.receivers = receivers;
        this.relativeFilePath = relativeFilePath;
        this.initReceiverLatch = new CountDownLatch(1);
        this.chunkProvider = new ChunkProvider(
                this.storageAdapter,
                this.objectStore,
                new TreePathElement(relativeFilePath)
        );
    }

    @Override
    public void run() {
        try {
            // check whether the own client is also in the list (should be usually, but you never know...)
            this.clientCounter = this.receivers.size();
            for (NodeLocation location : this.receivers) {
                if (location.getPeerAddress().equals(this.node.getPeerAddress())) {
                    this.clientCounter--;
                    break;
                }
            }

            this.chunkCountDownLatch = new CountDownLatch(this.clientCounter);
            this.initReceiverLatch.countDown();

            // check whether we got access to the file
            this.fileId = null;
            this.owner = null;
            try {
                PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(this.relativeFilePath);
                // if we are not the owner but have access to the file
                if (null != pathObject.getOwner() &&
                        ! this.node.getUser().getUserName().equals(pathObject.getOwner()) &&
                        AccessType.WRITE.equals(pathObject.getAccessType())) {
                    try {
                        this.fileId = this.node.getIdentifierManager().getValue(this.relativeFilePath);
                        this.owner = pathObject.getOwner();
                    } catch (InputOutputException e) {
                        logger.error("Failed to get file id for " + this.relativeFilePath + ". Message: " + e.getMessage());
                    }
                }

                // add file id also if the path is shared
                if (pathObject.isShared()) {
                    for (Sharer entry : pathObject.getSharers()) {
                        try {
                            // ask sharer's clients to get the changes too
                            List<NodeLocation> sharerLocations = this.nodeManager.getNodeLocations(entry.getUsername());

                            // only add one client of the sharer. He may propagate the change then
                            // to his clients, and if a conflict occurs, there will be a new file
                            if (! sharerLocations.isEmpty()) {
                                fileId = super.node.getIdentifierManager().getValue(pathObject.getAbsolutePath());
                                // Note that we do not add the sharer location again since these
                                // are assembled in FileOfferExchangeHandlerResult
                            }
                        } catch (InputOutputException e) {
                            logger.error("Could not get client locations of sharer " + entry.getUsername() + ". This sharer's clients do not get the file (change)");
                        }
                    }
                }
            } catch (InputOutputException e) {
                logger.error("Failed to read path object for " + this.relativeFilePath + ". Message: " + e.getMessage());
            }

            // the owner of a file is only added on a share request
            for (NodeLocation location : this.receivers) {
                UUID uuid = UUID.randomUUID();
                logger.info("Sending first chunk as subRequest of " + this.exchangeId + " with id " + uuid + " to client " + location.getPeerAddress().inetAddress().getHostName() + ":" + location.getPeerAddress().tcpPort());
                // add callback handler for sub request
                super.node.getObjectDataReplyHandler().addResponseCallbackHandler(uuid, this);

                this.sendChunk(
                        0, // first chunk
                        this.fileId,
                        this.owner,
                        uuid,
                        location
                );
            }
        } catch (Exception e) {
            logger.error("Failed to execute FilePushExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getAffectedFilePaths() {
        List<String> affectedPaths = new ArrayList<>();
        affectedPaths.add(this.relativeFilePath);

        return affectedPaths;
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof FilePushResponse)) {
            logger.error("Expected response to be instance of " + FilePushResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        if (- 1 < ((FilePushResponse) response).getChunkCounter()) {
            this.sendChunk(
                    ((FilePushResponse) response).getChunkCounter(),
                    this.fileId,
                    this.owner,
                    response.getExchangeId(),
                    new NodeLocation(
                            response.getClientDevice().getUserName(),
                            response.getClientDevice().getClientDeviceId(),
                            response.getClientDevice().getPeerAddress())
            );
        } else {
            // exchange is finished
            super.node.getObjectDataReplyHandler().removeResponseCallbackHandler(response.getExchangeId());
            super.onResponse(response);
            this.chunkCountDownLatch.countDown();
        }
    }

    @Override
    public FilePushExchangeHandlerResult getResult() {
        return new FilePushExchangeHandlerResult();
    }

    @Override
    public void await()
            throws InterruptedException {
        // only wait for parent if we actually have sent a request
        if (this.clientCounter > 0) {
            super.await();
        }

        // wait for receivers to be initialised
        this.initReceiverLatch.await();

        this.chunkCountDownLatch.await(MAX_FILE_WAITNG_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        // only wait for parent if we actually have sent a request
        if (this.clientCounter > 0) {
            super.await(timeout, timeUnit);
        }

        // wait for receivers to be initialised
        this.initReceiverLatch.await(timeout, timeUnit);

        this.chunkCountDownLatch.await(timeout, timeUnit);
    }

    @Override
    public boolean isCompleted() {
        return null != this.chunkCountDownLatch && 0L == this.chunkCountDownLatch.getCount();
    }

    /**
     * Send a chunk to another client
     *
     * @param chunkCounter The chunk counter
     * @param exchangeId   The exchange id for the request
     * @param receiver     The receiver which should get the chunk
     */
    protected void sendChunk(long chunkCounter, UUID fileId, String owner, UUID exchangeId, NodeLocation receiver) {
        Chunk chunk = new Chunk(
                "",
                "",
                new HashSet<>(),
                true,
                AccessType.WRITE,
                - 1,
                - 1,
                - 1,
                null
        );

        try {
            chunk = this.chunkProvider.getChunk(chunkCounter, CHUNK_SIZE);
        } catch (InputOutputException e) {
            logger.error("Failed to read the chunk " + chunkCounter + " of file " + this.relativeFilePath + " for exchange " + this.exchangeId + ". Aborting file push exchange. Message: " + e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            // requested chunk does not exist anymore
            logger.info("Detected file change during push exchange " + this.exchangeId + ". Starting to push again at chunk 0");
            try {
                chunk = this.chunkProvider.getChunk(0, CHUNK_SIZE);
            } catch (InputOutputException e1) {
                logger.error("Failed to read the chunk " + chunkCounter + " of file " + this.relativeFilePath + " for exchange " + this.exchangeId + " after detected file change. Aborting file push exchange. Message: " + e.getMessage());
            }
        }

        // check whether the chunk counter has changed
        StatusCode statusCode = (chunkCounter == chunk.getChunkCounter()) ? StatusCode.NONE : StatusCode.FILE_CHANGED;

        IRequest request = new FilePushRequest(
                exchangeId,
                statusCode,
                this.clientDevice,
                chunk.getChecksum(),
                fileId,
                owner,
                chunk.getAccessType(),
                chunk.getSharers(),
                this.relativeFilePath,
                chunk.isFile(),
                chunk.getChunkCounter(),
                CHUNK_SIZE,
                chunk.getTotalNrOfChunks(),
                chunk.getTotalFileSize(),
                chunk.getData(),
                receiver
        );

        logger.info("Sending chunk " + chunkCounter + " to client " + receiver.getPeerAddress().inetAddress().getHostAddress() + ":" + receiver.getPeerAddress().tcpPort());

        super.sendRequest(request);
    }
}
