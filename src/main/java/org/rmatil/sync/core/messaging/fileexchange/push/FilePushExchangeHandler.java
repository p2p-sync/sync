package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.network.api.*;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class FilePushExchangeHandler extends ANetworkHandler<FilePushExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferExchangeHandler.class);

    protected static final int CHUNK_SIZE = 1024;

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

    protected IClientManager clientManager;

    protected String relativeFilePath;

    public FilePushExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, ClientManager clientManager, IClient client, String relativeFilePath) {
        super(client);
        this.clientDevice = clientDevice;
        this.exchangeId = exchangeId;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
        this.relativeFilePath = relativeFilePath;
    }

    @Override
    public void run() {
        try {
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            for (ClientLocation location : clientLocations) {
                UUID uuid = UUID.randomUUID();
                logger.info("Sending first chunk as subRequest of " + this.exchangeId + " with id " + uuid + " to client " + location.getPeerAddress().inetAddress().getHostName() + ":" + location.getPeerAddress().tcpPort());
                // add callback handler for subrequest
                super.client.getObjectDataReplyHandler().addCallbackHandler(uuid, this);

                this.sendChunk(0, uuid, location);
            }
        } catch (Exception e) {
            logger.error("Failed to execute FilePushExchangeHandler. Message: " + e.getMessage());
        }
    }

    @Override
    public void onResponse(IResponse response) {
        if (response instanceof FilePushResponse) {
            if ( -1 < ((FilePushResponse) response).getChunkCounter()) {
                this.sendChunk(((FilePushResponse) response).getChunkCounter(), response.getExchangeId(), new ClientLocation(response.getClientDevice().getClientDeviceId(), response.getClientDevice().getPeerAddress()));
            } else {
                // exchange is finished
                this.countDownLatch.countDown();
            }
        }
    }

    @Override
    public FilePushExchangeHandlerResult getResult() {
        return new FilePushExchangeHandlerResult();
    }

    protected void sendChunk(long chunkCounter, UUID exchangeId, ClientLocation receiver) {
        IPathElement pathElement = new LocalPathElement(this.relativeFilePath);
        IFileMetaInfo fileMetaInfo;
        try {
            fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
        } catch (InputOutputException e) {
            logger.error("Could not fetch meta information about " + pathElement.getPath() + ". Message: " + e.getMessage());
            return;
        }

        // should round to the next bigger int value anyway
        int totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / CHUNK_SIZE);
        long fileChunkStartOffset = chunkCounter * CHUNK_SIZE;

        // storage adapter trims requests for a too large chunk
        byte[] content;
        try {
            content = this.storageAdapter.read(pathElement, fileChunkStartOffset, CHUNK_SIZE);
        } catch (InputOutputException e) {
            logger.error("Could not read file contents of " + pathElement.getPath() + " at offset " + fileChunkStartOffset + " bytes with chunk size of " + CHUNK_SIZE + " bytes");
            return;
        }

        Data data = new Data(content, false);

        IRequest request = new FilePushRequest(
                exchangeId,
                this.clientDevice,
                this.relativeFilePath,
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
