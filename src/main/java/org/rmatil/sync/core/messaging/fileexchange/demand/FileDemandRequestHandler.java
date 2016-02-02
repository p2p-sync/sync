package org.rmatil.sync.core.messaging.fileexchange.demand;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.security.IAccessManager;
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
    protected IStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The client to send responses
     */
    protected IClient client;

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

            if (! this.client.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.READ, this.request.getRelativeFilePath())) {
                logger.warn("Failed to get requested chunk due to missing access rights on file " + this.request.getRelativeFilePath() + " for user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(this.createErrorResponse(-1, -1));
                return;
            }

            IPathElement pathElement = new LocalPathElement(this.request.getRelativeFilePath());
            IFileMetaInfo fileMetaInfo;
            try {
                fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
            } catch (InputOutputException e) {
                logger.error("Could not fetch meta information about " + pathElement.getPath() + ". Message: " + e.getMessage());

                this.sendResponse(this.createErrorResponse(-1, -1));
                return;
            }

            int totalNrOfChunks = 0;
            Data data = null;
            if (fileMetaInfo.isFile()) {
                // should round to the next bigger int value anyway
                totalNrOfChunks = (int) Math.ceil(fileMetaInfo.getTotalFileSize() / CHUNK_SIZE);

                // restart the file exchange again if the other client requests a chunk we do not have
                // maybe due to a rewrite of the file content while syncing
                if (totalNrOfChunks < this.request.getChunkCounter()) {
                    // no chunk anymore, fileDemandExchangeHandler is then forced to check checksum -> re-download if not matching
                    this.sendResponse(this.createErrorResponse(-1, totalNrOfChunks));
                    return;
                }

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

            Set<Sharer> sharers = new HashSet<>();
            try {
                sharers = this.objectStore.getSharerManager().getSharer(this.request.getRelativeFilePath());
            } catch (InputOutputException e) {
                logger.error("Failed to read the sharers for file " + this.request.getRelativeFilePath() + ". Sending an empty sharer set. Message: " + e.getMessage());
            }

            String checksum = null;
            try {
                checksum = this.storageAdapter.getChecksum(pathElement);
            } catch (InputOutputException e) {
                logger.error("Could not generate checksum. Message: " + e.getMessage(), e);
            }

            IResponse response = new FileDemandResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    checksum,
                    this.request.getRelativeFilePath(),
                    fileMetaInfo.isFile(),
                    this.request.getChunkCounter(),
                    CHUNK_SIZE,
                    totalNrOfChunks,
                    fileMetaInfo.getTotalFileSize(),
                    data,
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    sharers
            );

            this.sendResponse(response);

        } catch (Exception e) {
            logger.error("Got exception in FileDemandRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    protected FileDemandResponse createErrorResponse(long chunkCounter, long totalNrOfChunks) {
        return new FileDemandResponse(
                this.request.getExchangeId(),
                new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                "",
                this.request.getRelativeFilePath(),
                true,
                chunkCounter,
                CHUNK_SIZE,
                totalNrOfChunks,
                - 1,
                null,
                new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                new HashSet<>()
        );
    }

    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    protected void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }
}
