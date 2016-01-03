package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
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
import sun.plugin.dom.exception.InvalidStateException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Send this request object to clients, to offer
 * a file creation / modification / deletion.
 */
public class FileOfferRequest implements IRequest {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferRequest.class);

    /**
     * The client device which sends this request
     */
    protected ClientDevice clientDevice;

    /**
     * The id of the file exchange
     */
    protected UUID exchangeId;

    protected List<ClientLocation> receiverAddresses;

    protected SerializableEvent event;

    protected IClient         client;
    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;
    protected MBassador       globalEventBus;

    /**
     * @param exchangeId   The id of the file exchange
     * @param clientDevice The client device which sends this request
     */
    public FileOfferRequest(UUID exchangeId, ClientDevice clientDevice, SerializableEvent event, List<ClientLocation> receiverAddresses) {
        this.clientDevice = clientDevice;
        this.exchangeId = exchangeId;
        this.receiverAddresses = receiverAddresses;
        this.event = event;
    }


    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return this.receiverAddresses;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    public void setGlobalEventBus(MBassador globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public UUID getExchangeId() {
        return exchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    @Override
    public void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new InvalidStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }

    @Override
    public void run() {
        try {
            LocalPathElement pathElement = new LocalPathElement(this.event.getPath());
            LocalPathElement origPathElement = new LocalPathElement(this.event.getPath());

            boolean hasAccepted = false;
            boolean hasConflict = false;

            switch (this.event.getEventName()) {
                case DeleteEvent.EVENT_NAME:
                    // create positive response if file exists
                    try {
                        if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                            hasAccepted = true;
                            hasConflict = false;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the file " + this.event.getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back an unaccepted offer");
                        hasAccepted = false;
                        hasConflict = false;
                    }
                    break;
                case MoveEvent.EVENT_NAME:
                    // overwrite path element to check with target
                    pathElement = new LocalPathElement(this.event.getNewPath());
                case CreateEvent.EVENT_NAME:
                case ModifyEvent.EVENT_NAME:
                    try {
                        if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                            // compare versions
                            if (this.hasVersionConflict(pathElement)) {
                                hasAccepted = true;
                                hasConflict = true;

                                this.createConflictFile(new LocalPathElement(this.event.getPath()));
                            } else {
                                hasAccepted = false;
                                hasConflict = false;
                            }
                        } else {
                            hasAccepted = true;
                            hasConflict = false;
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not check whether the file " + this.event.getPath() + " exists or not. Message: " + e.getMessage() + ". Sending back a conflict file");
                        hasAccepted = false;
                        hasConflict = false;
                    }
                    break;
            }

            this.sendResponse(this.createResponse(hasAccepted, hasConflict));
        } catch (Exception e) {
            logger.error("Failed to execute file offer request " + this.exchangeId + ". Message: " + e.getMessage(), e);
        }
    }

    protected FileOfferResponse createResponse(boolean hasAccepted, boolean hasConflict) {
        ClientDevice sendingClient = new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress());
        // the sender becomes the receiver
        ClientLocation receiver = new ClientLocation(this.clientDevice.getClientDeviceId(), this.clientDevice.getPeerAddress());

        return new FileOfferResponse(this.exchangeId, sendingClient, receiver, hasAccepted, hasConflict);
    }

    protected boolean hasVersionConflict(LocalPathElement pathElement) {
        String lastLocalFileVersionHash = null;
        String lastRemoteFileVersionHash = null;

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

                lastRemoteFileVersionHash = this.event.getHash();
            }
        } catch (InputOutputException e) {
            logger.error("Failed to check if file " + pathElement.getPath() + " exists. Message: " + e.getMessage() + ". Indicating that a conflict happened");
            return true;
        }

        if ((null != lastRemoteFileVersionHash && null == lastLocalFileVersionHash) ||
                (null == lastRemoteFileVersionHash && null != lastLocalFileVersionHash) ||
                (null != lastRemoteFileVersionHash && null != lastLocalFileVersionHash && ! lastLocalFileVersionHash.equals(lastRemoteFileVersionHash))) {
            logger.info("Detected conflict for fileExchange "
                    + this.exchangeId
                    + ": Remote version from client "
                    + this.clientDevice.getClientDeviceId()
                    + " was "
                    + ((lastRemoteFileVersionHash == null) ? "null" : lastRemoteFileVersionHash)
                    + ", local version was "
                    + ((lastLocalFileVersionHash == null) ? "null" : lastLocalFileVersionHash)
            );

            return true;
        }

        // no conflict happened
        return false;
    }

    protected void createConflictFile(LocalPathElement pathElement) {
        PathObject pathObject;
        try {
            Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
            String hash = indexPaths.get(pathElement.getPath());

            pathObject = this.objectStore.getObjectManager().getObject(hash);
        } catch (InputOutputException e) {
            logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened");
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
