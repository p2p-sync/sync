package org.rmatil.sync.core.messaging.sharingexchange.share;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ShareExchangeHandler extends ANetworkHandler<ShareExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(ShareExchangeHandler.class);

    /**
     * Wait a maximum of 2 minutes for a file exchange to complete
     */
    protected static final long MAX_FILE_WWAITNG_TIME = 120000L;

    /**
     * The chunk size to use for the whole file exchange
     */
    protected static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    protected IClientManager clientManager;

    protected ClientLocation receiverAddress;

    protected IStorageAdapter storageAdapter;

    protected UUID fileId;

    protected AccessType accessType;

    protected UUID exchangeId;

    protected boolean isFile;

    protected String relativeFilePath;

    public ShareExchangeHandler(IClient client, IClientManager clientManager, ClientLocation receiverAddress, IStorageAdapter storageAdapter, String relativeFilePath, AccessType accessType, UUID fileId, boolean isFile, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.receiverAddress = receiverAddress;
        this.relativeFilePath = relativeFilePath;
        this.accessType = accessType;
        this.fileId = fileId;
        this.exchangeId = exchangeId;
        this.isFile = isFile;
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

    }

    @Override
    public ShareExchangeHandlerResult getResult() {
        return null;
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
                this.relativeFilePath,
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
