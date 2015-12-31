package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * The request handler which decides how to
 * handle a file offer request. All other requests are handled by throwing an exception.
 *
 * @see FileOfferRequest
 */
public class FileOfferRequestHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferRequestHandler.class);

    /**
     * The client device of this handler
     */
    protected ClientDevice clientDevice;

    protected IObjectStore objectStore;

    protected IStorageAdapter storageAdapter;

    protected final List<IEvent> eventsToIgnore;
    protected final List<IEvent> eventsToAdditionallyAdd;

    /**
     * @param clientDevice The client which handles the file offerings
     * @param objectStore The object store to access local stored versions
     * @param storageAdapter The storage adapter to access the synchronized folder
     */
    public FileOfferRequestHandler(ClientDevice clientDevice, IObjectStore objectStore, IStorageAdapter storageAdapter, List<IEvent> ignoredEvents, List<IEvent> additionalEvents) {
        this.clientDevice = clientDevice;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
        this.eventsToIgnore = ignoredEvents;
        this.eventsToAdditionallyAdd = additionalEvents;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FileOfferRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        String lastLocalFileVersionHash = null;
        String lastRemoteFileVersionHash = null;

        // check if the file does exist locally, if not then we agree automatically and fetch the latest changes later
        IPathElement pathElement = new LocalPathElement(((FileOfferRequest) request).getRelativeFilePath());
        if (this.storageAdapter.exists(StorageType.FILE, pathElement)) {
            PathObject pathObject;
            try {
                Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
                String hash = indexPaths.get(((FileOfferRequest) request).getRelativeFilePath());

                pathObject = this.objectStore.getObjectManager().getObject(hash);
            } catch (InputOutputException e) {
                throw new SyncFailedException("Failed to read path object from object store. Message: " + e.getMessage(), e);
            }

            // compare local and remote file versions
            List<Version> localFileVersions = pathObject.getVersions();
            Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
            lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

            List<Version> remoteFileVersions = ((FileOfferRequest) request).getFileVersions();
            Version lastRemoteFileVersion = remoteFileVersions.size() > 0 ? remoteFileVersions.get(remoteFileVersions.size() - 1) : null;
            lastRemoteFileVersionHash = (null != lastRemoteFileVersion) ? lastRemoteFileVersion.getHash() : null;
        }

        // we accept each offer for now
        boolean acceptedOffer = true;
        boolean hasConflict = false;

        // check whether a different version exists locally
        if ((null != lastRemoteFileVersionHash && null == lastLocalFileVersionHash) ||
                (null == lastRemoteFileVersionHash && null != lastLocalFileVersionHash) ||
                (null != lastRemoteFileVersionHash && null != lastLocalFileVersionHash && ! lastLocalFileVersionHash.equals(lastRemoteFileVersionHash))) {
            logger.info("Detected conflict for fileExchange "
                    + ((FileOfferRequest) request).getExchangeId()
                    + ": Remote version from client "
                    + ((FileOfferRequest) request).getClientDevice().getClientDeviceId()
                    + " was "
                    + ((lastRemoteFileVersionHash == null) ? "null" : lastRemoteFileVersionHash)
                    + ", local version was "
                    + ((lastLocalFileVersionHash == null) ? "null" : lastLocalFileVersionHash)
            );

            hasConflict = true;


            IFileMetaInfo fileMetaInfo = this.storageAdapter.getMetaInformation(pathElement);

            String newFileName = Naming.getConflictFileName(((FileOfferRequest) request).getRelativeFilePath(), fileMetaInfo.isFile(), fileMetaInfo.getFileExtension(), this.clientDevice.getClientDeviceId().toString());
            IPathElement conflictFile = new LocalPathElement(newFileName);
            StorageType storageType = fileMetaInfo.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            IEvent moveEvent = new MoveEvent(
                    Paths.get(pathElement.getPath()),
                    Paths.get(conflictFile.getPath()),
                    ((FileOfferRequest) request).getRelativeFilePath(),
                    lastLocalFileVersionHash,
                    System.currentTimeMillis()
            );

            IEvent createEvent = new CreateEvent(
                    Paths.get(conflictFile.getPath()),
                    Paths.get(conflictFile.getPath()).getFileName().toString(),
                    lastLocalFileVersionHash,
                    System.currentTimeMillis()
            );

            // ignore the move event for the file syncer
            synchronized (this.eventsToIgnore) {
                this.eventsToIgnore.add(moveEvent);
            }

            // but announce the new conflict file
            synchronized (this.eventsToAdditionallyAdd) {
                this.eventsToAdditionallyAdd.add(createEvent);
            }

            // finally move local path element
            try {
                this.storageAdapter.move(storageType, pathElement, conflictFile);
            } catch (InputOutputException e) {
                logger.error("Can not move conflict file " + pathElement.getPath() + " to " + conflictFile.getPath() + ". Message: " + e.getMessage());
            }
        }

        logger.info("Sending back a FileOfferResponse. ExchangeId: "
                + ((FileOfferRequest) request).getExchangeId()
                + ", AcceptedOffer: "
                + acceptedOffer
                + ", HasConflict: "
                + hasConflict
        );

        return new FileOfferResponse(
                ((FileOfferRequest) request).getExchangeId(),
                this.clientDevice,
                acceptedOffer,
                hasConflict
        );
    }
}
