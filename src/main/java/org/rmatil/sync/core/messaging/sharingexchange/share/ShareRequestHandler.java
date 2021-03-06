package org.rmatil.sync.core.messaging.sharingexchange.share;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.core.ShareNaming;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.*;
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
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ShareRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(ShareRequestHandler.class);

    /**
     * The storage adapter to access the synchronized folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store to access versions
     */
    protected IObjectStore objectStore;

    /**
     * The client to send back messages
     */
    protected INode node;

    /**
     * The file share request from the sender
     */
    protected ShareRequest request;

    /**
     * The global event bus to add ignore events
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
        if (! (iRequest instanceof ShareRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + ShareRequest.class.getName());
        }

        this.request = (ShareRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Writing chunk " + this.request.getChunkCounter() + " for file " + this.request.getFileId() + " (" + this.request.getRelativePathToSharedFolder() + ") for exchangeId " + this.request.getExchangeId());

            // check whether the file id has been written previously
            String relativePath = this.node.getIdentifierManager().getKey(
                    this.request.getFileId()
            );

            if (null != relativePath &&
                    ! relativePath.startsWith(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName()) &&
                    ! relativePath.startsWith(Config.DEFAULT.getSharedWithOthersReadWriteFolderName())) {
                // we detected a circular reference for sharing
                logger.warn("Detected a circular reference for file " + this.request.getFileId() + ". It is already stored at " + relativePath + ". Denying the incoming share request for exchange " + this.request.getExchangeId());
                this.sendResponse(this.createDeniedResponse());
                return;
            }

            Path relPathToSharedFolder = Paths.get(this.request.getRelativePathToSharedFolder());
            if (null == relativePath) {
                // does also contain the filename

                if (relPathToSharedFolder.getNameCount() > 1) {
                    // also a parent is existing in the path

                    Path parent;
                    if (AccessType.WRITE == this.request.getAccessType()) {
                        parent = Paths.get(Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).resolve(relPathToSharedFolder.subpath(0, relPathToSharedFolder.getNameCount() - 1));
                    } else {
                        parent = Paths.get(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName()).resolve(relPathToSharedFolder.subpath(0, relPathToSharedFolder.getNameCount() - 1));
                    }

                    // place the file in the root if it's parent does not exist anymore
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, new TreePathElement(parent.toString()))) {
                        logger.info("Parent of file " + this.request.getRelativePathToSharedFolder() + " does not exist (anymore). Placing file at root of shared dir");
                        relPathToSharedFolder = relPathToSharedFolder.getFileName();
                    }
                }


                // find an unique file path and store it in the DHT
                if (AccessType.WRITE == this.request.getAccessType()) {
                    relativePath = Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + relPathToSharedFolder.toString();
                } else {
                    relativePath = Config.DEFAULT.getSharedWithOthersReadOnlyFolderName() + "/" + relPathToSharedFolder.toString();
                }

                relativePath = ShareNaming.getUniqueFileName(this.storageAdapter, relativePath, this.request.isFile());
                // add relativePath <-> fileId to DHT
                this.node.getIdentifierManager().addIdentifier(relativePath, this.request.getFileId());
            }

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;
            TreePathElement pathElement = new TreePathElement(relativePath);

            if (this.request.isFile() && StatusCode.FILE_CHANGED.equals(this.request.getStatusCode()) &&
                    this.storageAdapter.exists(storageType, pathElement)) {
                // we have to clean up the file again to prevent the
                // file being larger than expected after the change
                this.publishIgnoreModifyEvent(relativePath);
                this.publishIgnoreModifyOsEvent(relativePath);
                this.storageAdapter.persist(storageType, pathElement, new byte[0]);
            }

            if (this.request.isFile()) {
                try {
                    if (! this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                        this.publishIgnoreCreateEvent(relativePath);
                        this.publishIgnoreCreateOsEvent(relativePath);
                    } else {
                        this.publishIgnoreModifyEvent(relativePath);
                        this.publishIgnoreModifyOsEvent(relativePath);
                    }

                    this.storageAdapter.persist(StorageType.FILE, pathElement, this.request.getChunkCounter() * this.request.getChunkSize(), this.request.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " of file " + relativePath + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, pathElement)) {
                        this.publishIgnoreCreateEvent(relativePath);
                        this.publishIgnoreCreateOsEvent(relativePath);
                        this.storageAdapter.persist(StorageType.DIRECTORY, pathElement, null);
                    }
                } catch (InputOutputException e) {
                    logger.error("Could not create directory " + pathElement.getPath() + ". Message: " + e.getMessage());
                }
            }

            long requestingChunk = this.request.getChunkCounter();
            // chunk counter starts at 0
            if (this.request.getChunkCounter() + 1 == this.request.getTotalNrOfChunks()) {
                // now check that we got the same checksum for the file
                try {
                    String checksum = "";

                    // dirs may not have a checksum
                    if (this.request.isFile()) {
                        checksum = this.storageAdapter.getChecksum(pathElement);
                    }

                    if (null == this.request.getChecksum() || this.request.getChecksum().equals(checksum)) {
                        logger.info("Checksums match. Stopping exchange " + this.request.getExchangeId());
                        // checksums match or the other side failed to compute one
                        // -> indicate we got all chunks
                        requestingChunk = - 1;

                        // create the hash of the file / directory
                        String hash = Hash.hash(
                                org.rmatil.sync.event.aggregator.config.Config.DEFAULT.getHashingAlgorithm(),
                                Paths.get(this.storageAdapter.getRootDir().getPath()).resolve(pathElement.getPath()).toFile()
                        );

                        this.objectStore.onCreateFile(relativePath, hash);
                        PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(relativePath);
                        pathObject.setAccessType(this.request.getAccessType());
                        this.objectStore.getObjectManager().writeObject(pathObject);

                        // add owner
                        this.objectStore.getSharerManager().addOwner(
                                this.request.getClientDevice().getUserName(),
                                relativePath
                        );

                        // clean up all modify ignore events
                        this.globalEventBus.publish(new CleanModifyIgnoreEventsBusEvent(
                                relativePath
                        ));

                        // clean up all modify ignore events for the object store
                        this.globalEventBus.publish(new CleanModifyOsIgnoreEventsBusEvent(
                                relativePath
                        ));

                        // now we are save to finally notify the FileSyncer about a new file
                        this.globalEventBus.publish(new CreateBusEvent(
                                new CreateEvent(
                                        Paths.get(relativePath),
                                        Paths.get(relativePath).getFileName().toString(),
                                        hash,
                                        System.currentTimeMillis()
                                )
                        ));

                    } else {
                        logger.info("Checksums do not match (local: " + checksum + "/request:" + this.request.getChecksum() + "). Restarting to push file for exchange " + this.request.getExchangeId());
                        // restart to fetch the whole file
                        requestingChunk = 0;

                        this.publishIgnoreModifyEvent(relativePath);
                        this.publishIgnoreModifyOsEvent(relativePath);
                        this.storageAdapter.persist(storageType, pathElement, new byte[0]);
                    }
                } catch (InputOutputException e) {
                    logger.error("Failed to generate the checksum for file " + pathElement.getPath() + " on exchange " + this.request.getExchangeId() + ". Accepting the file. Message: " + e.getMessage());
                    requestingChunk = - 1;
                }
            } else {
                requestingChunk++;
            }

            this.sendResponse(this.createResponse(requestingChunk));

        } catch (Exception e) {
            logger.error("Got Error in ShareRequestHandler for exchange " + this.request.getExchangeId() + ": " + e.getMessage(), e);

            try {
                this.sendResponse(this.createResponse(StatusCode.ERROR, - 1));
            } catch (Exception e1) {
                logger.error("Failed to notify originating node about error in exchange " + this.request.getExchangeId() + ". Message: " + e1.getMessage(), e1);
            }
        }
    }

    /**
     * Creates a share response with the given chunk counter
     *
     * @param requestingChunk The chunk to request from the other client
     *
     * @return The created ShareResponse
     */
    protected ShareResponse createResponse(long requestingChunk) {
        return this.createResponse(StatusCode.ACCEPTED, requestingChunk);
    }

    /**
     * Creates an error share response with access denied
     *
     * @return The error share response
     */
    protected ShareResponse createDeniedResponse() {
        return this.createResponse(StatusCode.ACCESS_DENIED, - 1);
    }

    /**
     * Create a new response with the given status code and requesting chunk
     *
     * @param statusCode      The status code
     * @param requestingChunk The chunk to request
     *
     * @return The created response
     */
    protected ShareResponse createResponse(StatusCode statusCode, long requestingChunk) {
        return new ShareResponse(
                this.request.getExchangeId(),
                statusCode,
                new ClientDevice(
                        this.node.getUser().getUserName(),
                        this.node.getClientDeviceId(),
                        this.node.getPeerAddress()
                ),
                this.request.getFileId(),
                new NodeLocation(
                        this.request.getClientDevice().getUserName(),
                        this.request.getClientDevice().getClientDeviceId(),
                        this.request.getClientDevice().getPeerAddress()
                ),
                requestingChunk
        );
    }

    /**
     * Sends the given response back to the client
     *
     * @param response The response to send back
     */
    public void sendResponse(IResponse response) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.node.sendDirect(response.getReceiverAddress(), response);
    }

    protected void publishIgnoreModifyEvent(String relativePath) {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new ModifyEvent(
                        Paths.get(relativePath),
                        Paths.get(relativePath).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));

    }

    protected void publishIgnoreModifyOsEvent(String relativePath) {
        this.globalEventBus.publish(new IgnoreObjectStoreUpdateBusEvent(
                new ModifyEvent(
                        Paths.get(relativePath),
                        Paths.get(relativePath).toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));
    }

    protected void publishIgnoreCreateEvent(String relativePath) {
        this.globalEventBus.publish(new IgnoreBusEvent(
                new CreateEvent(
                        Paths.get(relativePath),
                        Paths.get(relativePath).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));
    }

    protected void publishIgnoreCreateOsEvent(String relativePath) {
        this.globalEventBus.publish(new IgnoreObjectStoreUpdateBusEvent(
                new CreateEvent(
                        Paths.get(relativePath),
                        Paths.get(relativePath).toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                )
        ));
    }

}
