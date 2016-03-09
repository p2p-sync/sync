package org.rmatil.sync.core.messaging.sharingexchange.share;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.chunk.Chunk;
import org.rmatil.sync.core.messaging.chunk.ChunkProvider;
import org.rmatil.sync.network.api.INode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ShareExchangeHandler extends ANetworkHandler<ShareExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(ShareExchangeHandler.class);

    /**
     * Wait a maximum of 2 minutes for a file exchange to complete
     */
    protected static final long MAX_FILE_WAITING_TIME = 120000L;

    /**
     * The chunk size to use for the whole file exchange
     */
    public static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    /**
     * The client location to which this share should be sent
     */
    protected NodeLocation receiverAddress;

    /**
     * The storage adapter to read the file chunks from
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The file id.
     */
    protected UUID fileId;

    /**
     * The access type which should be granted the other client for share
     */
    protected AccessType accessType;

    /**
     * Whether the shared path is a file
     */
    protected boolean isFile;

    /**
     * The relative path in the synced folder
     * which should be shared
     */
    protected String relativeFilePath;

    /**
     * The relative file path in the shared folder
     */
    protected String relativeFilePathToSharedFolder;

    /**
     * The exchange id of this handler
     */
    protected UUID exchangeId;

    /**
     * A count down latch to check if all clients have received all chunks.
     * We have to use this one instead of {@link ANetworkHandler#countDownLatch} since
     * we are sending file chunks as subrequests one by one
     */
    protected CountDownLatch chunkCountDownLatch;

    /**
     * Provides chunk from a file
     */
    protected ChunkProvider chunkProvider;

    /**
     * @param client                         The client to send messages
     * @param receiverAddress                The receiver address, i.e. the sharers location
     * @param storageAdapter                 The storage adapter to read the chunks
     * @param objectStore                    The object store
     * @param relativeFilePath               The relative file path on our disk
     * @param relativeFilePathToSharedFolder The relative path to the folder / file which is actually shared (if it is the same, the relative path is "")
     * @param accessType                     The access type which should be granted to the sharer
     * @param fileId                         The file id which should be used as identifier of the file
     * @param isFile                         Whether the path represents a file or directory
     * @param exchangeId                     The exchangeId
     */
    public ShareExchangeHandler(INode client, NodeLocation receiverAddress, ITreeStorageAdapter storageAdapter, IObjectStore objectStore, String relativeFilePath, String relativeFilePathToSharedFolder, AccessType accessType, UUID fileId, boolean isFile, UUID exchangeId) {
        super(client);
        this.receiverAddress = receiverAddress;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.relativeFilePath = relativeFilePath;
        this.relativeFilePathToSharedFolder = relativeFilePathToSharedFolder;
        this.accessType = accessType;
        this.fileId = fileId;
        this.isFile = isFile;
        this.exchangeId = exchangeId;
        this.chunkCountDownLatch = new CountDownLatch(1);

        this.chunkProvider = new ChunkProvider(
                this.storageAdapter,
                this.objectStore,
                new TreePathElement(relativeFilePath)
        );
    }

    @Override
    public void run() {
        try {
            logger.info("Sharing file " + this.fileId + " with client on " + this.receiverAddress.getPeerAddress().inetAddress().getHostName() + ":" + this.receiverAddress.getPeerAddress().tcpPort());

            this.sendChunk(0, this.exchangeId, receiverAddress);

        } catch (Exception e) {
            logger.error("Got exception in ShareExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof ShareResponse)) {
            logger.error("Expected response to be instance of " + ShareResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        if (- 1 < ((ShareResponse) response).getChunkCounter() &&
                StatusCode.DENIED != ((ShareResponse) response).getStatusCode()) {
            this.sendChunk(
                    ((ShareResponse) response).getChunkCounter(),
                    response.getExchangeId(),
                    new NodeLocation(
                            response.getClientDevice().getUserName(),
                            response.getClientDevice().getClientDeviceId(),
                            response.getClientDevice().getPeerAddress()
                    )
            );
        } else {
            // exchange is finished
            super.node.getObjectDataReplyHandler().removeResponseCallbackHandler(response.getExchangeId());

            super.onResponse(response);

            this.chunkCountDownLatch.countDown();
        }
    }

    @Override
    public void await()
            throws InterruptedException {
        // do not await on parent here, since the countdown latch will
        // be re-initiated on sendRequest()
        this.chunkCountDownLatch.await(MAX_FILE_WAITING_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        // do not await on parent here, since the countdown latch will
        // be re-initiated on sendRequest()
        this.chunkCountDownLatch.await(MAX_FILE_WAITING_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isCompleted() {
        return null != this.chunkCountDownLatch && 0L == this.chunkCountDownLatch.getCount();
    }

    @Override
    public ShareExchangeHandlerResult getResult() {
        return new ShareExchangeHandlerResult();
    }

    protected void sendChunk(long chunkCounter, UUID exchangeId, NodeLocation sharer) {
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
            logger.error("Failed to read the chunk " + chunkCounter + " of file " + this.relativeFilePath + " for exchange " + this.exchangeId + ". Aborting share exchange. Message: " + e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            // requested chunk does not exist anymore
            logger.info("Detected file change during push exchange " + this.exchangeId + ". Starting to push again at chunk 0");
            try {
                chunk = this.chunkProvider.getChunk(0, CHUNK_SIZE);
            } catch (InputOutputException e1) {
                logger.error("Failed to read the chunk " + chunkCounter + " of file " + this.relativeFilePath + " for exchange " + this.exchangeId + " after detected file change. Aborting share exchange. Message: " + e.getMessage());
            }
        }

        // check whether the chunk counter has changed
        StatusCode statusCode = (chunkCounter == chunk.getChunkCounter()) ? StatusCode.NONE : StatusCode.FILE_CHANGED;

        IRequest request = new ShareRequest(
                exchangeId,
                statusCode,
                new ClientDevice(
                        super.node.getUser().getUserName(),
                        super.node.getClientDeviceId(),
                        super.node.getPeerAddress()
                ),
                sharer,
                this.fileId,
                chunk.getChecksum(),
                this.accessType,
                this.relativeFilePathToSharedFolder,
                chunk.isFile(),
                chunk.getChunkCounter(),
                chunk.getTotalNrOfChunks(),
                chunk.getTotalFileSize(),
                chunk.getData(),
                CHUNK_SIZE
        );

        logger.info("Sending chunk " + chunk.getChunkCounter() + " to sharer " + sharer.getPeerAddress().inetAddress().getHostAddress() + ":" + sharer.getPeerAddress().tcpPort());

        super.sendRequest(request);
    }
}
