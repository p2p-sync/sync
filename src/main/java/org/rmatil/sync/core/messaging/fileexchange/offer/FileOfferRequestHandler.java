package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Handles an incoming {@link FileOfferRequest} by checking whether a file on the same
 * path exists but has a different version than the requested one.
 * <p>
 * Sends a {@link FileOfferResponse} back to the client which has sent the request.
 */
public class FileOfferRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected FileOfferRequest     request;
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
        if (! (iRequest instanceof FileOfferRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileOfferRequest.class.getName());
        }

        this.request = (FileOfferRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            LocalPathElement pathElement = new LocalPathElement(this.request.getEvent().getPath());

            boolean hasAccepted = false;
            boolean hasConflict = false;

            switch (this.request.getEvent().getEventName()) {
                case DeleteEvent.EVENT_NAME:
                    // create positive response if file exists
                    try {
                        if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                            hasAccepted = true;
                            hasConflict = false;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the file " + this.request.getEvent().getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back an unaccepted offer");
                        hasAccepted = false;
                        hasConflict = false;
                    }
                    break;
                case MoveEvent.EVENT_NAME:
                    // overwrite path element to check with target
                    pathElement = new LocalPathElement(this.request.getEvent().getNewPath());
                case CreateEvent.EVENT_NAME:
                case ModifyEvent.EVENT_NAME:
                    try {
                        if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                            // compare versions
                            if (this.hasVersionConflict(pathElement)) {
                                hasAccepted = true;
                                hasConflict = true;

                                this.createConflictFile(new LocalPathElement(this.request.getEvent().getPath()));
                            } else {
                                hasAccepted = true;
                                hasConflict = false;
                            }
                        } else {
                            hasAccepted = true;
                            hasConflict = false;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the file " + this.request.getEvent().getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back a conflict file");
                        hasAccepted = false;
                        hasConflict = false;
                    }
                    break;
            }

            this.sendResponse(this.createResponse(hasAccepted, hasConflict));
        } catch (Exception e) {
            logger.error("Failed to handle file offer request " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Send the given response as result to the request
     *
     * @param iResponse The response to send
     */
    protected void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }

    /**
     * Create a new FileOfferResponse
     *
     * @param hasAccepted Whether this client has accepted the file offer request
     * @param hasConflict Whether this client has detected a conflict
     *
     * @return The FileOfferResponse representing the result of this client
     */
    protected FileOfferResponse createResponse(boolean hasAccepted, boolean hasConflict) {
        ClientDevice sendingClient = new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress());
        // the sender becomes the receiver
        ClientLocation receiver = new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress());

        return new FileOfferResponse(
                this.request.getExchangeId(),
                sendingClient,
                receiver,
                hasAccepted,
                hasConflict
        );
    }

    /**
     * Check whether a conflict exists for the given path element
     *
     * @param pathElement The path element of the received request
     *
     * @return True, if a conflict has been detected, false otherwise
     */
    protected boolean hasVersionConflict(LocalPathElement pathElement) {
        String lastLocalFileVersionHash = null;
        String eventHash = null;
        // could be null, if the file contains only the first version
        String beforeLastRemoteFileVersionHash = this.request.getEvent().getHashBefore();

        // check if the file does exist locally, if not then we agree automatically and fetch the latest changes later
        try {
            if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                PathObject pathObject;
                try {
                    Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
                    String hash = indexPaths.get(pathElement.getPath());

                    pathObject = this.objectStore.getObjectManager().getObject(hash);
                } catch (InputOutputException e) {
                    logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened");
                    return true;
                }

                // compare local and remote file versions
                List<Version> localFileVersions = pathObject.getVersions();
                Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
                lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

                eventHash = this.request.getEvent().getHash();
            }
        } catch (InputOutputException e) {
            logger.error("Failed to check if file " + pathElement.getPath() + " exists. Message: " + e.getMessage() + ". Indicating that a conflict happened");
            return true;
        }

        if ((null != eventHash && null == lastLocalFileVersionHash) || // if both versions are null then we have no conflict
                (null == eventHash && null != lastLocalFileVersionHash && ! this.request.getEvent().getEventName().equals(DeleteEvent.EVENT_NAME)) || // If the remote has a null hash and the request is not a delete event (there we do not care about the hash)
                (null != eventHash && null != lastLocalFileVersionHash &&
                        (! lastLocalFileVersionHash.equals(eventHash) && // if we do not have the same content in the file as remote and ...
                                ! lastLocalFileVersionHash.equals(beforeLastRemoteFileVersionHash)) // ... if we do not have the version before the hash of the event
                )) {

            logger.info("Detected conflict for fileExchange "
                    + this.request.getExchangeId()
                    + ": Remote version from client "
                    + this.request.getClientDevice().getClientDeviceId()
                    + " was "
                    + ((eventHash == null) ? "null" : eventHash)
                    + ", local version was "
                    + ((lastLocalFileVersionHash == null) ? "null" : lastLocalFileVersionHash)
            );

            return true;
        }

        // no conflict happened
        return false;
    }

    /**
     * Create a conflict file for the given path element
     *
     * @param pathElement The path element for which to create a conflict file
     */
    protected void createConflictFile(LocalPathElement pathElement) {
        PathObject pathObject;
        try {
            Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
            String hash = indexPaths.get(pathElement.getPath());

            pathObject = this.objectStore.getObjectManager().getObject(hash);
        } catch (InputOutputException e) {
            logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened", e);
            return;
        }

        // compare local and remote file versions
        List<Version> localFileVersions = pathObject.getVersions();
        Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
        String lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

        Path conflictFilePath;
        try {
            IFileMetaInfo fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);
            conflictFilePath = Paths.get(Naming.getConflictFileName(pathElement.getPath(), true, fileMetaInfo.getFileExtension(), this.client.getClientDeviceId().toString()));
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new MoveEvent(
                            Paths.get(pathElement.getPath()),
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));
            this.globalEventBus.publish(new CreateBusEvent(
                    new CreateEvent(
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));

        } catch (InputOutputException e) {
            logger.error("Can not read meta information for file " + pathElement.getPath() + ". Moving the conflict file failed");
            return;
        }

        try {
            this.storageAdapter.move(StorageType.FILE, pathElement, new LocalPathElement(conflictFilePath.toString()));
        } catch (InputOutputException e) {
            logger.error("Can not move conflict file " + pathElement.getPath() + " to " + conflictFilePath.toString() + ". Message: " + e.getMessage());
        }
    }
}
