package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.ConflictHandler;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Handles an incoming {@link FileOfferRequest} by checking whether a file on the same
 * path exists but has a different version than the requested one.
 * <p>
 * Sends a {@link FileOfferResponse} back to the client which has sent the request.
 */
public class FileOfferRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferRequestHandler.class);

    protected enum CONFLICT_TYPE {
        CONFLICT,

        NO_CONFLICT_REQUEST_OBSOLETE,

        NO_CONFLICT_REQUEST_REQUIRED
    }

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
     * The file offer request from the sender
     */
    protected FileOfferRequest request;

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
        if (! (iRequest instanceof FileOfferRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileOfferRequest.class.getName());
        }

        this.request = (FileOfferRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            LocalPathElement pathElement;
            if ((null != this.request.getOwner() && this.node.getUser().getUserName().equals(this.request.getOwner())) ||
                    null != this.request.getFileId()) {
                logger.debug("Using the path registered with the file id " + this.request.getFileId() + " to answer the file offer request");
                // we have to use our path: if we are either the owner or a sharer
                pathElement = new LocalPathElement(this.node.getIdentifierManager().getKey(this.request.getFileId()));
            } else {
                logger.debug("Using the path from the request " + this.request.getEvent().getPath() + " to answer the file offer request");
                pathElement = new LocalPathElement(this.request.getEvent().getPath());
            }

            logger.info("Processing file offer request for path " + pathElement.getPath());

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, pathElement.getPath())) {
                logger.warn("Failed to positively return the offer from user " + this.request.getClientDevice().getUserName() + " for file " + pathElement.getPath() + " due to missing access rights on exchange " + this.request.getExchangeId());
                this.sendResponse(this.createResponse(StatusCode.ACCESS_DENIED));
                return;
            }

            StatusCode statusCode = StatusCode.ACCEPTED;

            switch (this.request.getEvent().getEventName()) {
                case DeleteEvent.EVENT_NAME:
                    // create positive response if file or directory exists
                    try {
                        if ((this.request.getEvent().isFile() && this.storageAdapter.exists(StorageType.FILE, pathElement)) ||
                                this.storageAdapter.exists(StorageType.DIRECTORY, pathElement)) {
                            statusCode = StatusCode.ACCEPTED;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the path " + pathElement.getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back an unaccepted offer");
                        statusCode = StatusCode.DENIED;
                    }
                    break;
                case MoveEvent.EVENT_NAME:
                    // TODO: what is intended to be done, if target already exists?
                    // overwrite path element to check with target
                    // moves are not sent from clients of different users
                    pathElement = new LocalPathElement(this.request.getEvent().getNewPath());
                case CreateEvent.EVENT_NAME:
                case ModifyEvent.EVENT_NAME:
                    try {
                        if (this.request.getEvent().isFile() && this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                            // compare versions
                            CONFLICT_TYPE hasVersionConflict = this.hasVersionConflict(pathElement);
                            if (CONFLICT_TYPE.CONFLICT == hasVersionConflict) {
                                statusCode = StatusCode.CONFLICT;
                                Path conflictFile = ConflictHandler.createConflictFile(
                                        this.globalEventBus,
                                        this.node.getClientDeviceId().toString(),
                                        this.objectStore,
                                        this.storageAdapter,
                                        pathElement
                                );

                                // move element in the IdentifierManager too
                                UUID fileId = this.node.getIdentifierManager().getValue(pathElement.getPath());
                                try {
                                    if (null != conflictFile && null != fileId) {
                                        this.node.getIdentifierManager().removeIdentifier(pathElement.getPath());
                                        this.node.getIdentifierManager().addIdentifier(conflictFile.toString(), fileId);
                                    }
                                } catch (InputOutputException e) {
                                    logger.error("Failed to move conflicting file with id " + fileId + " on path " + pathElement.getPath() + " to new path too. Message: " + e.getMessage());
                                }

                            } else if (CONFLICT_TYPE.NO_CONFLICT_REQUEST_REQUIRED == hasVersionConflict) {
                                statusCode = StatusCode.ACCEPTED;
                            } else {
                                statusCode = StatusCode.REQUEST_OBSOLETE;
                            }
                        } else {
                            // we accept any offer if it is a directory, whether it exists or not
                            statusCode = StatusCode.ACCEPTED;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the file " + pathElement.getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back a conflict file");
                        statusCode = StatusCode.DENIED;
                    }
                    break;
            }

            this.sendResponse(this.createResponse(statusCode));
        } catch (Exception e) {
            logger.error("Failed to handle file offer request " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Send the given response as result to the request
     *
     * @param response The response to send
     */
    protected void sendResponse(IResponse response) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.node.sendDirect(
                response.getReceiverAddress(),
                response
        );
    }

    /**
     * Creates a new FileOfferResponse with the given status code
     *
     * @return The FileOfferResponse representing the result of this client
     */
    protected FileOfferResponse createResponse(StatusCode statusCode) {
        ClientDevice sendingClient = new ClientDevice(this.node.getUser().getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress());
        // the sender becomes the receiver
        NodeLocation receiver = new NodeLocation(
                this.request.getClientDevice().getUserName(),
                this.request.getClientDevice().getClientDeviceId(),
                this.request.getClientDevice().getPeerAddress()
        );

        return new FileOfferResponse(
                this.request.getExchangeId(),
                statusCode,
                sendingClient,
                receiver
        );
    }

    /**
     * Check whether a conflict exists for the given path element
     *
     * @param pathElement The path element of the received request
     *
     * @return True, if a conflict has been detected, false otherwise
     */
    protected CONFLICT_TYPE hasVersionConflict(LocalPathElement pathElement) {
        String lastLocalFileVersionHash = null;
        String eventHash = null;
        // could be null, if the file contains only the first version
        String beforeLastRemoteFileVersionHash = this.request.getEvent().getHashBefore();

        // check if the file does exist locally, if not then we agree automatically and fetch the latest changes later
        try {
            if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                PathObject pathObject;
                try {
                    pathObject = this.objectStore.getObjectManager().getObjectForPath(pathElement.getPath());
                } catch (InputOutputException e) {
                    logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened");
                    return CONFLICT_TYPE.CONFLICT;
                }

                // compare local and remote file versions
                List<Version> localFileVersions = pathObject.getVersions();
                Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
                lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

                eventHash = this.request.getEvent().getHash();
            } else if (this.storageAdapter.exists(StorageType.DIRECTORY, pathElement)) {
                // we do not care, if we get a modify event for a directory only
                return CONFLICT_TYPE.NO_CONFLICT_REQUEST_REQUIRED;
            }
        } catch (InputOutputException e) {
            logger.error("Failed to check if file " + pathElement.getPath() + " exists. Message: " + e.getMessage() + ". Indicating that a conflict happened");
            return CONFLICT_TYPE.CONFLICT;
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

            return CONFLICT_TYPE.CONFLICT;
        }

        // no conflict happened
        if ((null == eventHash && null == lastLocalFileVersionHash) || (null != eventHash && eventHash.equals(lastLocalFileVersionHash))) {
            // we do not need a further request, if we already have the same hash as offered
            return CONFLICT_TYPE.NO_CONFLICT_REQUEST_OBSOLETE;
        }

        return CONFLICT_TYPE.NO_CONFLICT_REQUEST_REQUIRED;
    }
}
