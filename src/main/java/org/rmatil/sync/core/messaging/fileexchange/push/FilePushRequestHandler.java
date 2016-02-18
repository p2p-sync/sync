package org.rmatil.sync.core.messaging.fileexchange.push;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.AddOwnerAndAccessTypeToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class FilePushRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FilePushRequestHandler.class);

    /**
     * The storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The object store to access versions
     */
    protected IObjectStore objectStore;

    /**
     * The client to send back messages
     */
    protected INode node;

    /**
     * The file push request from the sender
     */
    protected FilePushRequest request;

    /**
     * The global event bus to add ignore events
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
    public void setNode(INode INode) {
        this.node = INode;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof FilePushRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FilePushRequest.class.getName());
        }

        this.request = (FilePushRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Writing chunk " + this.request.getChunkCounter() + " for file " + this.request.getRelativeFilePath() + " for exchangeId " + this.request.getExchangeId());

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, this.request.getRelativeFilePath())) {
                logger.warn("Failed to write chunk " + this.request.getChunkCounter() + " for file " + this.request.getRelativeFilePath() + " due to missing access rights of user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(this.createResponse(- 1));
                return;
            }

            IPathElement localPathElement = new LocalPathElement(this.request.getRelativeFilePath());

            // TODO: check whether the file isDeleted on each write, there might be a concurrent incoming delete request
            // -> affected FilePaths? in ObjectDataReply?

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            if (0 == this.request.getChunkCounter()) {
                // add sharers to object store only on the first request
                this.publishAddOwnerAndAccessTypeToObjectStore();
                this.publishAddSharerToObjectStore();
            }

            if (this.request.isFile() && StatusCode.FILE_CHANGED.equals(this.request.getStatusCode()) &&
                    this.storageAdapter.exists(storageType, localPathElement)) {
                // we have to clean up the file again to prevent the
                // file being larger than expected after the change
                this.publishIgnoreModifyEvent();
                this.storageAdapter.persist(storageType, localPathElement, new byte[0]);
            }

            if (this.request.isFile()) {
                try {
                    if (! this.storageAdapter.exists(StorageType.FILE, localPathElement)) {
                        this.publishIgnoreCreateEvent();
                    } else {
                        this.publishIgnoreModifyEvent();
                    }

                    this.storageAdapter.persist(StorageType.FILE, localPathElement, this.request.getChunkCounter() * this.request.getChunkSize(), this.request.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " of file " + this.request.getRelativeFilePath() + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, localPathElement)) {
                        this.publishIgnoreCreateEvent();
                        this.storageAdapter.persist(StorageType.DIRECTORY, localPathElement, null);
                    }
                } catch (InputOutputException e) {
                    logger.error("Could not create directory " + localPathElement.getPath() + ". Message: " + e.getMessage());
                }
            }

            long requestingChunk = this.request.getChunkCounter();
            if (this.request.getChunkCounter() == this.request.getTotalNrOfChunks()) {
                // now check that we got the same checksum for the file
                try {
                    String checksum = "";

                    // dirs may not have a checksum
                    if (this.request.isFile()) {
                        checksum = this.storageAdapter.getChecksum(localPathElement);
                    }

                    if (null == this.request.getChecksum() || this.request.getChecksum().equals(checksum)) {
                        logger.info("Checksums match. Stopping exchange " + this.request.getExchangeId());
                        // checksums match or the other side failed to compute one
                        // -> indicate we got all chunks
                        requestingChunk = - 1;
                    } else {
                        logger.info("Checksums do not match (local: " + checksum + "/request:" + this.request.getChecksum() + "). Restarting to push file for exchange " + this.request.getExchangeId());
                        // restart to fetch the whole file
                        requestingChunk = 0;

                        this.publishIgnoreModifyEvent();
                        this.storageAdapter.persist(storageType, localPathElement, new byte[0]);
                    }
                } catch (InputOutputException e) {
                    logger.error("Failed to generate the checksum for file " + localPathElement.getPath() + " on exchange " + this.request.getExchangeId() + ". Accepting the file. Message: " + e.getMessage());
                    requestingChunk = - 1;
                }
            } else {
                requestingChunk++;
            }

            this.sendResponse(this.createResponse(requestingChunk));
        } catch (Exception e) {
            logger.error("Error in FilePushRequestHandler for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a file push response with the given chunk counter
     *
     * @param requestingChunk The chunk to request from the other client
     *
     * @return The created FilePushResponse
     */
    protected FilePushResponse createResponse(long requestingChunk) {
        return new FilePushResponse(
                this.request.getExchangeId(),
                StatusCode.ACCEPTED,
                new ClientDevice(this.node.getUser().getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress()),
                this.request.getRelativeFilePath(),
                new NodeLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                requestingChunk
        );
    }

    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    protected void sendResponse(IResponse iResponse) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.node.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }

    protected void publishIgnoreModifyEvent() {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new ModifyEvent(
                        Paths.get(this.request.getRelativeFilePath()),
                        Paths.get(this.request.getRelativeFilePath()).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));

    }

    protected void publishIgnoreCreateEvent() {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new CreateEvent(
                        Paths.get(this.request.getRelativeFilePath()),
                        Paths.get(this.request.getRelativeFilePath()).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));
    }

    protected void publishAddSharerToObjectStore() {
        this.globalEventBus.publish(new AddSharerToObjectStoreBusEvent(
                this.request.getRelativeFilePath(),
                this.request.getSharers()
        ));
    }

    protected void publishAddOwnerAndAccessTypeToObjectStore() {
        this.globalEventBus.publish(new AddOwnerAndAccessTypeToObjectStoreBusEvent(
                this.request.getOwner(),
                this.request.getAccessType(),
                this.request.getRelativeFilePath()
        ));
    }
}
