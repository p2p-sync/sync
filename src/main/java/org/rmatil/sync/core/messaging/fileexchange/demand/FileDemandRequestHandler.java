package org.rmatil.sync.core.messaging.fileexchange.demand;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
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
public class FileDemandRequestHandler implements ILocalStateRequestCallback {

    private final static Logger logger = LoggerFactory.getLogger(FileDemandRequestHandler.class);

    /**
     * The chunk size to use for the whole file exchange
     */
    protected static final int CHUNK_SIZE = 1024 * 1024; // 1MB

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected FileDemandRequest    request;
    protected MBassador<IBusEvent> globalEventBus;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
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
    public void setClient(IClient iClient) {
        this.client = iClient;
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

            IPathElement pathElement = new LocalPathElement(this.request.getRelativeFilePath());
            IFileMetaInfo fileMetaInfo;
            try {
                fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
            } catch (InputOutputException e) {
                logger.error("Could not fetch meta information about " + pathElement.getPath() + ". Message: " + e.getMessage());

                this.sendResponse(
                        new FileDemandResponse(
                                this.request.getExchangeId(),
                                new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                                this.request.getRelativeFilePath(),
                                true,
                                -1,
                                -1,
                                -1,
                                -1,
                                null,
                                new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress())
                        )
                );

                return;
            }

            // TODO: check access for file by comparing requesting user and shared with users in object store

            int totalNrOfChunks = 0;
            Data data = null;
            if (fileMetaInfo.isFile()) {
                // should round to the next bigger int value anyway
                totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / CHUNK_SIZE);
                long fileChunkStartOffset = this.request.getChunkCounter() * CHUNK_SIZE;

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

            IResponse response = new FileDemandResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    this.request.getRelativeFilePath(),
                    fileMetaInfo.isFile(),
                    this.request.getChunkCounter(),
                    CHUNK_SIZE,
                    totalNrOfChunks,
                    fileMetaInfo.getTotalFileSize(),
                    data,
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress())
            );

            this.sendResponse(response);

        } catch (Exception e) {
            logger.error("Got exception in FileDemandRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    public void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }
}
