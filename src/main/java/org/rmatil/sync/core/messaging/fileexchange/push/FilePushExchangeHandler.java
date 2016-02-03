package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
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
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
    protected IStorageAdapter storageAdapter;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    /**
     * The client manager to access client locations
     */
    protected IClientManager clientManager;

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

    protected List<ClientLocation> receivers;

    public FilePushExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IClientManager clientManager, IClient client, IObjectStore objectStore, List<ClientLocation> receivers, String relativeFilePath) {
        super(client);
        this.clientDevice = clientDevice;
        this.exchangeId = exchangeId;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
        this.objectStore = objectStore;
        this.receivers = receivers;
        this.relativeFilePath = relativeFilePath;
    }

    @Override
    public void run() {
        try {
            // check whether the own client is also in the list (should be usually, but you never know...)
            int clientCounter = this.receivers.size();
            for (ClientLocation location : this.receivers) {
                if (location.getPeerAddress().equals(this.client.getPeerAddress())) {
                    clientCounter--;
                    break;
                }
            }

            this.chunkCountDownLatch = new CountDownLatch(clientCounter);

            // check, whether there is a fileId already present,
            // e.g. made in an earlier push request (or on another client)
            if (null == super.client.getIdentifierManager().getValue(this.relativeFilePath)) {
                // add a file id
                this.client.getIdentifierManager().addIdentifier(this.relativeFilePath, UUID.randomUUID());
            }

            // the owner of a file is only added on a share request

            for (ClientLocation location : this.receivers) {
                UUID uuid = UUID.randomUUID();
                logger.info("Sending first chunk as subRequest of " + this.exchangeId + " with id " + uuid + " to client " + location.getPeerAddress().inetAddress().getHostName() + ":" + location.getPeerAddress().tcpPort());
                // add callback handler for sub request
                super.client.getObjectDataReplyHandler().addResponseCallbackHandler(uuid, this);

                this.sendChunk(0, uuid, location);
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
            this.sendChunk(((FilePushResponse) response).getChunkCounter(), response.getExchangeId(), new ClientLocation(response.getClientDevice().getClientDeviceId(), response.getClientDevice().getPeerAddress()));
        } else {
            // exchange is finished
            super.client.getObjectDataReplyHandler().removeResponseCallbackHandler(response.getExchangeId());

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
        super.await();
        this.chunkCountDownLatch.await(MAX_FILE_WAITNG_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        super.await(timeout, timeUnit);
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
    protected void sendChunk(long chunkCounter, UUID exchangeId, ClientLocation receiver) {
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

            // restart the exchange, if the requested chunk counter is bigger than expected
            if (totalNrOfChunks < chunkCounter) {
                chunkCounter = 0;
            }

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

        Set<Sharer> sharers = new HashSet<>();
        try {
            sharers = this.objectStore.getSharerManager().getSharer(this.relativeFilePath);
        } catch (InputOutputException e) {
            logger.error("Failed to read the sharers for file " + this.relativeFilePath + ". Sending an empty sharer set. Message: " + e.getMessage());
        }

        AccessType accessType = AccessType.WRITE;
        String owner = null;
        try {
            PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(this.relativeFilePath);
            accessType = pathObject.getAccessType();
            owner = pathObject.getOwner();
        } catch (InputOutputException e) {
            logger.error("Failed to get AccessType for file " + this.relativeFilePath + " Sending access type write. Message: " + e.getMessage());
        }

        String checksum = null;
        try {
            if (fileMetaInfo.isFile()) {
                checksum = this.storageAdapter.getChecksum(pathElement);
            } else {
                // dirs may not have checksums
                checksum = "";
            }
        } catch (InputOutputException e) {
            logger.error("Could not generate checksum. Message: " + e.getMessage(), e);
        }

        IRequest request = new FilePushRequest(
                exchangeId,
                StatusCode.NONE,
                this.clientDevice,
                checksum,
                owner,
                accessType,
                sharers,
                this.relativeFilePath,
                fileMetaInfo.isFile(),
                chunkCounter,
                CHUNK_SIZE,
                totalNrOfChunks,
                fileMetaInfo.getTotalFileSize(),
                data,
                receiver
        );

        logger.info("Sending chunk " + chunkCounter + " to client " + receiver.getPeerAddress().inetAddress().getHostAddress() + ":" + receiver.getPeerAddress().tcpPort());

        super.sendRequest(request);
    }
}
