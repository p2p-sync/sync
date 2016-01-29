package org.rmatil.sync.core.messaging.sharingexchange.share;

import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    protected static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    /**
     * The client location to which this share should be sent
     */
    protected ClientLocation receiverAddress;

    /**
     * The storage adapter to read the file chunks from
     */
    protected IStorageAdapter storageAdapter;

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
     * @param client                         The client to send messages
     * @param receiverAddress                The receiver address, i.e. the sharers location
     * @param storageAdapter                 The storage adapter to read the chunks
     * @param relativeFilePath               The relative file path on our disk
     * @param relativeFilePathToSharedFolder The relative path to the folder / file which is actually shared (if it is the same, the relative path is "")
     * @param accessType                     The access type which should be granted to the sharer
     * @param fileId                         The file id which should be used as identifier of the file
     * @param isFile                         Whether the path represents a file or directory
     * @param exchangeId                     The exchangeId
     */
    public ShareExchangeHandler(IClient client, ClientLocation receiverAddress, IStorageAdapter storageAdapter, String relativeFilePath, String relativeFilePathToSharedFolder, AccessType accessType, UUID fileId, boolean isFile, UUID exchangeId) {
        super(client);
        this.receiverAddress = receiverAddress;
        this.storageAdapter = storageAdapter;
        this.relativeFilePath = relativeFilePath;
        this.relativeFilePathToSharedFolder = relativeFilePathToSharedFolder;
        this.accessType = accessType;
        this.fileId = fileId;
        this.isFile = isFile;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            logger.info("Sharing file " + this.fileId + " with client on " + this.receiverAddress.getPeerAddress().inetAddress().getHostName() + ":" + this.receiverAddress.getPeerAddress().tcpPort());

            this.chunkCountDownLatch = new CountDownLatch(1);

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

        if (- 1 < ((ShareResponse) response).getChunkCounter()) {
            this.sendChunk(((ShareResponse) response).getChunkCounter(), response.getExchangeId(), new ClientLocation(response.getClientDevice().getClientDeviceId(), response.getClientDevice().getPeerAddress()));
        } else {
            // exchange is finished
            super.client.getObjectDataReplyHandler().removeResponseCallbackHandler(response.getExchangeId());

            super.onResponse(response);

            this.chunkCountDownLatch.countDown();
        }
    }

    @Override
    public void await()
            throws InterruptedException {
        super.await();
        this.chunkCountDownLatch.await(MAX_FILE_WAITING_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        super.await();
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

    protected void sendChunk(long chunkCounter, UUID exchangeId, ClientLocation sharer) {
        IPathElement pathElement = new LocalPathElement(this.relativeFilePath);
        IFileMetaInfo fileMetaInfo;
        try {
            fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
        } catch (InputOutputException e) {
            logger.error("Could not fetch meta information about " + pathElement.getPath() + ". Message: " + e.getMessage());
            return;
        }

        int totalNrOfChunks = 0;
        Data data = null;
        if (fileMetaInfo.isFile()) {
            // should round to the next bigger int value anyway
            totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / CHUNK_SIZE);
            long fileChunkStartOffset = chunkCounter * CHUNK_SIZE;

            // storage adapter trims requests for a too large chunk
            byte[] content;
            try {
                content = this.storageAdapter.read(pathElement, fileChunkStartOffset, CHUNK_SIZE);
            } catch (InputOutputException e) {
                logger.error("Could not read file contents of " + pathElement.getPath() + " at offset " + fileChunkStartOffset + " bytes with chunk size of " + CHUNK_SIZE + " bytes");
                return;
            }

            data = new Data(content, false);
        }

        IRequest request = new ShareRequest(
                exchangeId,
                new ClientDevice(
                        super.client.getUser().getUserName(),
                        super.client.getClientDeviceId(),
                        super.client.getPeerAddress()
                ),
                sharer,
                this.fileId,
                this.accessType,
                this.relativeFilePathToSharedFolder,
                fileMetaInfo.isFile(),
                chunkCounter,
                totalNrOfChunks,
                fileMetaInfo.getTotalFileSize(),
                data,
                CHUNK_SIZE
        );

        logger.info("Sending chunk " + chunkCounter + " to sharer " + sharer.getPeerAddress().inetAddress().getHostAddress() + ":" + sharer.getPeerAddress().tcpPort());

        super.sendRequest(request);
    }
}
