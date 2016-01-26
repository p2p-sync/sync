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
import org.rmatil.sync.version.core.model.Sharer;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class ShareRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(ShareRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected ShareRequest         request;
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

            // if the chunk counter is greater than 0
            // we only modify the existing file, so we generate an ignore modify event
            if (this.request.getChunkCounter() > 0) {
                String hashOfFilePath = this.objectStore.getObjectManager().getIndex().getSharedPaths().get(this.request.getFileId());
                PathObject pathObject = this.objectStore.getObjectManager().getObject(hashOfFilePath);

                String relPathToSyncedFolder;

                if (AccessType.WRITE == this.request.getAccessType()) {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + pathObject.getAbsolutePath();
                } else {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadOnlyFolderName() + "/" + pathObject.getAbsolutePath();
                }

                pathElement = new LocalPathElement(relPathToSyncedFolder);

                IEvent modifyEvent = new ModifyEvent(
                        Paths.get(pathObject.getAbsolutePath()),
                        Paths.get(pathObject.getAbsolutePath()).getFileName().toString(),
                        "weIgnoreTheHash",
                        System.currentTimeMillis()
                );

                // ignore syncing of file event
                this.globalEventBus.publish(new IgnoreBusEvent(modifyEvent));
            } else {

                String relPathToSyncedFolder;

                if (AccessType.WRITE == this.request.getAccessType()) {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadWriteFolderName() + "/" + this.request.getRelativePathToSharedFolder();
                } else {
                    relPathToSyncedFolder = Config.DEFAULT.getSharedWithOthersReadOnlyFolderName() + "/" + this.request.getRelativePathToSharedFolder();
                }

                String uniqueFilePath = this.getUniqueFileName(relPathToSyncedFolder, this.request.isFile());

                pathElement = new LocalPathElement(uniqueFilePath);

                try {
                    // once we determined where we have to put the path, we can create the relation
                    // between the fileId and the filepath in the object store
                    this.objectStore.onCreateFile(uniqueFilePath, null);
                    PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(uniqueFilePath);
                    pathObject.setFileId(this.request.getFileId());
                    pathObject.setIsShared(true);
                    pathObject.getSharers().add(new Sharer(
                            this.request.getClientDevice().getUserName(),
                            this.request.getAccessType()
                    ));
                    this.objectStore.getObjectManager().writeObject(pathObject);


                    // since we generated an unique filename, the file is guaranteed to not exist
                    // and therefore, we ignore a create event until the whole file is written
                    IEvent createEvent = new CreateEvent(
                            Paths.get(uniqueFilePath),
                            Paths.get(uniqueFilePath).getFileName().toString(),
                            "weIgnoreTheHash",
                            System.currentTimeMillis()
                    );

                    // ignore syncing of file event
                    this.globalEventBus.publish(new IgnoreBusEvent(createEvent));
                    // ignore updating of the object store since we created the entry manually...
                    this.globalEventBus.publish(new IgnoreObjectStoreUpdateBusEvent(createEvent));

                } catch (InputOutputException e) {
                    logger.error("Can not create a unique filename for file " + this.request.getFileId() + ". Message: " + e.getMessage() + ". Just checking the chunk counters...");
                }
            }

            // actually write the file
            if (this.request.isFile()) {
                try {
                    this.storageAdapter.persist(
                            StorageType.FILE,
                            pathElement,
                            this.request.getChunkCounter() * this.request.getChunkSize(),
                            this.request.getData().getContent()
                    );
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " for shared file " + pathElement + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, pathElement)) {
                        this.storageAdapter.persist(StorageType.DIRECTORY, pathElement, null);
                    }

                } catch (InputOutputException e) {
                    logger.error("Could not create shared directory " + pathElement.getPath() + ". Message: " + e.getMessage());
                }
            }


            long requestingChunk = this.request.getChunkCounter();
            if (this.request.getChunkCounter() == this.request.getTotalNrOfChunks()) {
                // indicate we got all chunks and do not expect any further updates
                requestingChunk = - 1;

                // once we completed the file transfer, we can create the hash
                // and omit a CreateEvent to propagate the new file to all other own clients
                PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(pathElement.getPath());
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
                requestingChunk++;
            }

            IResponse response = new ShareResponse(
                    this.request.getExchangeId(),
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
            logger.error("Error in ShareRequestHandler for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
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
    protected String getUniqueFileName(String relativePath, boolean isFile)
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
                String tmpFileName = oldFileName.substring(0, Math.max(0, firstIndexOfDot - 1));
                // tmpFileName := myFile (1)
                tmpFileName = tmpFileName + " (" + ctr + ")";
                // tmpfileName := myFile (1).rar.zip
                tmpFileName = oldFileName.substring(firstIndexOfDot + 1, oldFileName.length());

                newFileName = tmpFileName;
            } else {
                // no dot in the filename -> just append the ctr
                newFileName = oldFileName + " (" + ctr + ")";
            }

        }


        // replace the **last** occurrence of the filename
        int lastIndex = relativePath.lastIndexOf(oldFileName);

        return relativePath.substring(0, lastIndex).concat(newFileName);
    }
}
