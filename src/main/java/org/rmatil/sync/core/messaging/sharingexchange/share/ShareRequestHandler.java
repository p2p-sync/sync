package org.rmatil.sync.core.messaging.sharingexchange.share;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreObjectStoreUpdateBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
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

import java.nio.file.Paths;

public class ShareRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(ShareRequestHandler.class);

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
    protected IClient client;

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
        if (! (iRequest instanceof ShareRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + ShareRequest.class.getName());
        }

        this.request = (ShareRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Writing chunk " + this.request.getChunkCounter() + " for file " + this.request.getFileId() + " (" + this.request.getRelativePathToSharedFolder() + ") for exchangeId " + this.request.getExchangeId());

            IPathElement pathElement;
            PathObject pathObject;

            // check if a file already exists in the DHT for the fileId,
            // maybe the file just changed while transmitting
            String relativePath = this.client.getIdentifierManager().getKey(this.request.getFileId());

            // setup shared folder and object store if the file does not exist yet
            if (0 == this.request.getChunkCounter() && null == relativePath) {
                // create shared dirs, if not exists
                this.createSharedDirs();

                // make a local relative path for the file
                String relPathToSyncedFolder;
                if (AccessType.WRITE == this.request.getAccessType()) {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + this.request.getRelativePathToSharedFolder();
                } else {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadOnlyFolderName() + "/" + this.request.getRelativePathToSharedFolder();
                }

                // create a unique file name in the shared folder
                relativePath = this.getUniqueFileName(relPathToSyncedFolder, this.request.isFile());

                // add the fileId so that each client can fetch it from the original
                this.client.getIdentifierManager().addIdentifier(relativePath, this.request.getFileId());

                this.objectStore.onCreateFile(relativePath, null);
                // adds the owner to the file but not as sharer
                // since we did not share the file with anyone yet
                this.objectStore.getSharerManager().addOwner(
                        this.request.getClientDevice().getUserName(),
                        relativePath
                );

                // also set the access type
                pathObject = this.objectStore.getObjectManager().getObjectForPath(relativePath);
                pathObject.setAccessType(this.request.getAccessType());
                this.objectStore.getObjectManager().writeObject(pathObject);

                // since we generated an unique filename, the file is guaranteed to not exist
                // and therefore, we ignore a create event until the whole file is written
                IEvent createEvent = new CreateEvent(
                        Paths.get(relativePath),
                        Paths.get(relativePath).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                );

                // ignore syncing of file event
                this.globalEventBus.publish(new IgnoreBusEvent(createEvent));
                // ignore updating of the object store since we created the entry manually...
                this.globalEventBus.publish(new IgnoreObjectStoreUpdateBusEvent(createEvent));


                pathElement = new LocalPathElement(relativePath);
            } else {
                // we have written a chunk of the file already -> get the file name
                pathObject = this.objectStore.getObjectManager().getObjectForPath(
                        this.client.getIdentifierManager().getKey(this.request.getFileId())
                );

                pathElement = new LocalPathElement(pathObject.getAbsolutePath());

                StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;
                if (this.request.isFile() && StatusCode.FILE_CHANGED.equals(this.request.getStatusCode())) {
                    // we have to clean up the file again to prevent the
                    // file being larger than expected after the change
                    this.storageAdapter.persist(storageType, pathElement, new byte[0]);

                    IEvent modifyEvent = new ModifyEvent(
                            Paths.get(pathObject.getAbsolutePath()),
                            Paths.get(pathObject.getAbsolutePath()).getFileName().toString(),
                            "weIgnoreTheHash",
                            System.currentTimeMillis()
                    );

                    // ignore syncing of file event
                    this.globalEventBus.publish(new IgnoreBusEvent(modifyEvent));
                }
            }

            IEvent modifyEvent = new ModifyEvent(
                    Paths.get(pathObject.getAbsolutePath()),
                    Paths.get(pathObject.getAbsolutePath()).getFileName().toString(),
                    "weIgnoreTheHash",
                    System.currentTimeMillis()
            );

            // ignore syncing of file event
            this.globalEventBus.publish(new IgnoreBusEvent(modifyEvent));

            // now actually write the file
            if (this.request.isFile()) {
                try {
                    this.storageAdapter.persist(StorageType.FILE, pathElement, this.request.getChunkCounter() * this.request.getChunkSize(), this.request.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " of file " + relativePath + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, pathElement)) {
                        this.storageAdapter.persist(StorageType.DIRECTORY, pathElement, null);
                    }
                } catch (InputOutputException e) {
                    logger.error("Could not create directory " + pathElement.getPath() + ". Message: " + e.getMessage());
                }
            }

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            long requestingChunk = this.request.getChunkCounter();
            if (this.request.getChunkCounter() == this.request.getTotalNrOfChunks()) {
                // now check that we got the same checksum for the file
                try {
                    String checksum = "";

                    // dirs may not have a checksum
                    if (this.request.isFile()) {
                        checksum = this.storageAdapter.getChecksum(pathElement);
                    }

                    if (null == this.request.getChecksum() || this.request.getChecksum().equals(checksum)) {
                        logger.info("Checksums match. Stopping share exchange " + this.request.getExchangeId());
                        // checksums match or the other side failed to compute one
                        // -> indicate we got all chunks
                        requestingChunk = - 1;

                        // once we completed the file transfer, we can create the hash
                        // and omit a CreateEvent to propagate the new file to all other own clients
                        String hash = Hash.hash(
                                org.rmatil.sync.event.aggregator.config.Config.DEFAULT.getHashingAlgorithm(),
                                this.storageAdapter.read(pathElement)
                        );
                        pathObject.getVersions().add(new Version(hash));

                        // now write the updated path object
                        this.objectStore.getObjectManager().writeObject(pathObject);
                        // omit a file create event to the file syncer
                        this.globalEventBus.publish(
                                new CreateBusEvent(
                                        new CreateEvent(
                                                Paths.get(pathElement.getPath()),
                                                Paths.get(pathElement.getPath()).getFileName().toString(),
                                                "weIgnoreTheHash",
                                                System.currentTimeMillis()
                                        )
                                )
                        );

                    } else {
                        logger.info("Checksums do not match. Restarting share exchange " + this.request.getExchangeId());
                        // restart to fetch the whole file
                        requestingChunk = 0;

                        // ignore again the syncing of the reset
                        this.globalEventBus.publish(new IgnoreBusEvent(modifyEvent));

                        this.storageAdapter.persist(storageType, pathElement, new byte[0]);
                    }
                } catch (InputOutputException e) {
                    logger.error("Failed to generate the checksum for file " + pathElement.getPath() + " on exchange " + this.request.getExchangeId() + ". Accepting the file. Message: " + e.getMessage());
                    requestingChunk = - 1;
                }
            } else {
                requestingChunk++;
            }

            IResponse response = new ShareResponse(
                    this.request.getExchangeId(),
                    StatusCode.ACCEPTED,
                    new ClientDevice(
                            this.client.getUser().getUserName(),
                            this.client.getClientDeviceId(),
                            this.client.getPeerAddress()
                    ),
                    this.request.getFileId(),
                    new ClientLocation(
                            this.request.getClientDevice().getClientDeviceId(),
                            this.request.getClientDevice().getPeerAddress()
                    ),
                    requestingChunk
            );

            this.sendResponse(response);

        } catch (Exception e) {
            logger.error("Got exception in ShareRequestHandler for exchange " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Create the sharedWithOthers (read/write) or sharedWithOthers (read) folders
     * if the do not exist yet.
     *
     * @throws InputOutputException If creating the dirs failed
     */
    protected void createSharedDirs()
            throws InputOutputException {
        IPathElement readOnlySharedFolder = new LocalPathElement(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName());
        IPathElement readWriteSharedFolder = new LocalPathElement(Config.DEFAULT.getSharedWithOthersReadWriteFolderName());

        if (! this.storageAdapter.exists(StorageType.DIRECTORY, readOnlySharedFolder)) {
            this.storageAdapter.persist(StorageType.DIRECTORY, readOnlySharedFolder, null);
        }

        if (! this.storageAdapter.exists(StorageType.DIRECTORY, readWriteSharedFolder)) {
            this.storageAdapter.persist(StorageType.DIRECTORY, readWriteSharedFolder, null);
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

    /**
     * Returns a unique filename for the given relative path
     *
     * @param relativePath The relative path to find a unique filename for
     * @param isFile       Whether the given path is a file or not
     *
     * @return The unique file name
     *
     * @throws InputOutputException If checking whether the path exists or not fails
     */
    public String getUniqueFileName(String relativePath, boolean isFile)
            throws InputOutputException {
        String oldFileName = Paths.get(relativePath).getFileName().toString();
        String newFileName = oldFileName;

        StorageType storageType = isFile ? StorageType.FILE : StorageType.DIRECTORY;

        String pathToFileWithoutFileName = Naming.getPathWithoutFileName(oldFileName, relativePath);

        int ctr = 1;
        while (this.storageAdapter.exists(storageType, new LocalPathElement(pathToFileWithoutFileName + "/" + newFileName))) {
            int firstIndexOfDot = oldFileName.indexOf(".");

            if (- 1 != firstIndexOfDot) {
                // myFile.rar.zip
                // tmpFileName := myFile
                String tmpFileName = oldFileName.substring(0, Math.max(0, firstIndexOfDot));
                // tmpFileName := myFile (1)
                tmpFileName = tmpFileName + " (" + ctr + ")";
                // tmpfileName := myFile (1).rar.zip
                tmpFileName = tmpFileName.concat(oldFileName.substring(firstIndexOfDot, oldFileName.length()));

                newFileName = tmpFileName;
            } else {
                // no dot in the filename -> just append the ctr
                newFileName = oldFileName + " (" + ctr + ")";
            }

            ctr++;
        }

        // replace the **last** occurrence of the filename
        int lastIndex = relativePath.lastIndexOf(oldFileName);

        return relativePath.substring(0, lastIndex).concat(newFileName);
    }
}
