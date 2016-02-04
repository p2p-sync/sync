package org.rmatil.sync.core.syncer.sharing;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.api.ISharingSyncer;
import org.rmatil.sync.core.exception.SharingFailedException;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.exception.UnsharingFailedException;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedExchangeHandler;
import org.rmatil.sync.core.syncer.sharing.event.ShareEvent;
import org.rmatil.sync.core.syncer.sharing.event.UnshareEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

public class SharingSyncer implements ISharingSyncer {

    private static final Logger logger = LoggerFactory.getLogger(SharingSyncer.class);

    protected IClient         client;
    protected IClientManager  clientManager;
    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;


    public SharingSyncer(IClient client, IClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore) {
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
    }

    @Override
    public void sync(IShareEvent sharingEvent)
            throws SharingFailedException, UnsharingFailedException {
        // share event

        // TODO: share events recursively from parent to children (only if folder)

        if (sharingEvent instanceof ShareEvent) {
            this.syncShareEvent((ShareEvent) sharingEvent);
        }

        if (sharingEvent instanceof UnshareEvent) {
            this.syncUnshareEvent((UnshareEvent) sharingEvent);
        }

    }

    /**
     * Share the specified file with the given sharer
     *
     * @param sharingEvent The sharing event to propagate
     */
    public void syncShareEvent(ShareEvent sharingEvent)
            throws SharingFailedException {
        UUID exchangeId = UUID.randomUUID();

        UUID fileId;
        try {
            fileId = this.client.getIdentifierManager().getValue(sharingEvent.getRelativePath().toString());
        } catch (InputOutputException e) {
            String msg = "Could not find fileId for file " + sharingEvent.getRelativePath().toString() + ". Message: " + e.getMessage();
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        boolean isFile = true;
        try {
            isFile = this.storageAdapter.isFile(new LocalPathElement(sharingEvent.getRelativePath().toString()));
        } catch (InputOutputException e) {
            String msg = "Can not determine whether the path " + sharingEvent.getRelativePath().toString() + " is a file or directory. Aborting share";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // Now ask all own clients to add the sharer to their object store
        SharedExchangeHandler sharedExchangeHandler = new SharedExchangeHandler(
                this.client,
                this.clientManager,
                this.objectStore,
                sharingEvent.getUsernameToShareWith(),
                sharingEvent.getAccessType(),
                sharingEvent.getRelativePath().toString(),
                exchangeId
        );

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, sharedExchangeHandler);

        Thread sharedExchangeHandlerThread = new Thread(sharedExchangeHandler);
        sharedExchangeHandlerThread.setName("SharedExchangeHandlerThread-" + exchangeId);
        sharedExchangeHandlerThread.start();

        try {
            sharedExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for all client to respond to the sharedExchange " + exchangeId);
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (! sharedExchangeHandler.isCompleted()) {
            String msg = "SharedExchangeHandler should be completed after awaiting. Did not send share request to sharer. Own clients may be inconsistent until next sync.";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // own clients did update their object store too, so we can now notify one client of the sharer
        ClientLocation sharerLocation = this.getClientLocationFromSharer(sharingEvent.getUsernameToShareWith());

        String relativePathToSharedFolder;
        try {
            relativePathToSharedFolder = this.getRelativePathToSharedFolder(sharingEvent.getRelativePath().toString(), sharingEvent.getUsernameToShareWith(), sharingEvent.getAccessType());
        } catch (InputOutputException e) {
            logger.error("Can not determine the relative path to the shared folder. We continue with a shared file at the root of the shared directory. Message: " + e.getMessage(), e);
            relativePathToSharedFolder = "";
        }

        ShareExchangeHandler shareExchangeHandler = new ShareExchangeHandler(
                this.client,
                sharerLocation,
                this.storageAdapter,
                this.objectStore,
                sharingEvent.getRelativePath().toString(),
                relativePathToSharedFolder,
                sharingEvent.getAccessType(),
                fileId,
                isFile,
                exchangeId
        );

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, shareExchangeHandler);

        Thread shareExchangeHandlerThread = new Thread(shareExchangeHandler);
        shareExchangeHandlerThread.setName("ShareExchangeHandler-" + exchangeId);
        shareExchangeHandlerThread.start();

        try {
            shareExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for shareExchangeHandler " + exchangeId + " to complete. Message: " + e.getMessage(), e);
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (! shareExchangeHandler.isCompleted()) {
            String msg = "ShareExchangeHandler should be completed after awaiting. Sync " + exchangeId + " will likely be failed. Aborting";
            logger.error(msg);
            throw new SyncFailedException(msg);
        }

        logger.info("Completed sharing of file " + sharingEvent.getRelativePath() + " with user " + sharingEvent.getUsernameToShareWith() + " (" + sharingEvent.getAccessType() + ")");
    }

    /**
     * Sync the unshare event with the own clients and then with the sharer itself
     *
     * @param unshareEvent The unshare event to sync
     *
     * @throws UnsharingFailedException If unsharing failed
     */
    public void syncUnshareEvent(UnshareEvent unshareEvent)
            throws UnsharingFailedException {
        UUID exchangeId = UUID.randomUUID();

        UUID fileId;
        try {
            fileId = this.client.getIdentifierManager().getValue(unshareEvent.getRelativePath().toString());
        } catch (InputOutputException e) {
            String msg = "Could not fetch fileId for file " + unshareEvent.getRelativePath().toString() + ". Message: " + e.getMessage();
            logger.error(msg, e);
            throw new UnsharingFailedException(msg, e);
        }

        // unshare on own clients
        UnsharedExchangeHandler unsharedExchangeHandler = new UnsharedExchangeHandler(
                this.client,
                this.clientManager,
                this.objectStore,
                unshareEvent.getRelativePath().toString(),
                fileId,
                unshareEvent.getUsernameToShareWith(),
                exchangeId
        );

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, unsharedExchangeHandler);

        Thread unsharedExchangeHandlerThread = new Thread(unsharedExchangeHandler);
        unsharedExchangeHandlerThread.setName("UnsharedExchangeHandler-" + exchangeId);
        unsharedExchangeHandlerThread.start();

        try {
            unsharedExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for unsharing " + exchangeId + " to complete on own clients. Message: " + e.getMessage(), e);
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (unsharedExchangeHandler.isCompleted()) {
            String msg = "UnsharedExchangeHandler should be completed after awaiting. Unsharing might not be complete on own clients. Aborting";
            logger.error(msg);
            throw new UnsharingFailedException(msg);
        }

        // unshare with sharer

        ClientLocation sharerLocation = this.getClientLocationFromSharer(unshareEvent.getUsernameToShareWith());

        UnshareExchangeHandler unshareExchangeHandler = new UnshareExchangeHandler(
                this.client,
                sharerLocation,
                fileId,
                exchangeId
        );

        this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, unshareExchangeHandler);

        Thread unshareExchangeHandlerThread = new Thread(unshareExchangeHandler);
        unshareExchangeHandlerThread.setName("UnshareExchangeHandler-" + exchangeId);
        unshareExchangeHandlerThread.start();

        try {
            unshareExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for unsharing " + exchangeId + " to complete on own clients. Message: " + e.getMessage(), e);
        }

        this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (unshareExchangeHandler.isCompleted()) {
            String msg = "UnshareExchangeHandler should be completed after awaiting. Unsharing might not be complete on sharer's clients";
            logger.error(msg);
            throw new UnsharingFailedException(msg);
        }

        logger.info("Completed unsharing of file " + unshareEvent.getRelativePath() + " with user " + unshareEvent.getUsernameToShareWith() + " (" + unshareEvent.getAccessType() + ")");
    }

    /**
     * Relativizes the given path to its most upper parent which is also shared with the given sharer.
     * Example:
     * Having a given path <i>syncedFolder/aDir/bDir/cDir/myFile.txt</i>, and a most upper shared directory of the
     * given sharer at <i>syncedFolder/aDir/bDir</i>, then this method will return <i>cDir/myFile.txt</i>,
     * so that the file can be placed in the sharers directory at the correct path.
     *
     * @param relativeFilePath The relative path of the file in the synced folder
     * @param username         The sharer to check for
     * @param accessType       The access type
     *
     * @return The relativized path
     *
     * @throws InputOutputException If reading the storage adapter / object store failed
     */
    public String getRelativePathToSharedFolder(String relativeFilePath, String username, AccessType accessType)
            throws InputOutputException {
        // look up if there is any direct parent directory which is also shared with the given path.
        // if so, then we "add" the given file to that directory, resolving the path relatively to that one

        Path origPath = Paths.get(relativeFilePath);
        Path path = Paths.get(relativeFilePath);

        int pathCtr = path.getNameCount() - 1;
        while (pathCtr > 1) {
            Path subPath = path.subpath(0, pathCtr);
            IPathElement subPathElement = new LocalPathElement(subPath.toString());

            if (this.storageAdapter.exists(StorageType.DIRECTORY, subPathElement) ||
                    this.storageAdapter.exists(StorageType.FILE, subPathElement)) {
                // check whether the parent is shared
                PathObject parentObject = this.objectStore.getObjectManager().getObjectForPath(subPathElement.getPath());


                if (! parentObject.isShared()) {
                    // parent is not shared at all
                    break;
                } else {
                    // now check if there is a sharer present for the given username and access type
                    boolean sharerIsPresent = false;
                    for (Sharer sharer : parentObject.getSharers()) {
                        if (sharer.getUsername().equals(username) && sharer.getAccessType().equals(accessType)) {
                            // ok, we found him
                            sharerIsPresent = true;
                        }
                    }

                    if (! sharerIsPresent) {
                        break;
                    }
                }

                // there is a parent which is also shared with the given user
                if (subPath.getNameCount() == 1) {
                    // we tested the most upper path, so we can break safely here
                    // -> actually prevent an IllegalArgumentException for subpath
                    break;
                } else {
                    pathCtr--;
                }
            }
        }

        // once we get there, path contains the most upper path which is also shared with the given sharer.
        // Therefore, we can resolve the given relativePath to the most upper one, and will then get
        // the path in the shared folder
        if (relativeFilePath.equals(path.toString())) {
            // do not relativize the top level path
            return relativeFilePath;
        } else {
            return origPath.subpath(pathCtr, origPath.getNameCount() - 1).toString();
        }
    }

    /**
     * Returns a single client location from the given sharer
     *
     * @param sharer The sharer of which to get the client location
     *
     * @return The found client location
     */
    public ClientLocation getClientLocationFromSharer(String sharer)
            throws SharingFailedException {
        List<ClientLocation> otherClientsLocations;
        try {
            otherClientsLocations = this.clientManager.getClientLocations(sharer);
        } catch (InputOutputException e) {
            logger.error("Could not fetch locations from " + sharer + ". Message: " + e.getMessage(), e);
            throw new SharingFailedException("Could not fetch locations from user " + sharer + ". Error: " + e.getMessage());
        }

        if (otherClientsLocations.isEmpty()) {
            String msg = "No client of user " + sharer + " is online. Sharing failed";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        return otherClientsLocations.get(0);
    }
}
