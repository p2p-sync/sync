package org.rmatil.sync.core.syncer.sharing;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.api.ISharingSyncer;
import org.rmatil.sync.core.exception.SharingFailedException;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.sharingexchange.offer.ShareOfferExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.offer.ShareOfferExchangeHandlerResult;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareExchangeHandler;
import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedExchangeHandler;
import org.rmatil.sync.core.syncer.sharing.event.ShareEvent;
import org.rmatil.sync.core.syncer.sharing.event.UnshareEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.User;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
            throws SharingFailedException {
        // share event

        // TODO: share events recursively from parent to children (only if folder)

        if (sharingEvent instanceof ShareEvent) {
            this.syncShareEvent((ShareEvent) sharingEvent);
        } else if (sharingEvent instanceof UnshareEvent) {
            this.syncUnshareEvent((UnshareEvent) sharingEvent);
        }

    }

    /**
     * Share the specified file with the given sharer
     *
     * @param sharingEvent The sharing event to propagate
     */
    public void syncShareEvent(ShareEvent sharingEvent) {
        UUID exchangeId = UUID.randomUUID();

        // propose UUID and negotiate sharing with all own clients.
        // client has to deny the offering if he already has a fileId for the given path -> send the id back
        // if any client did not accept -> deny sharing by throwing an exception -> feedback to end-user CLI
        ShareOfferExchangeHandler shareOfferExchangeHandler = new ShareOfferExchangeHandler(
                this.client,
                this.clientManager,
                sharingEvent.getRelativePath().toString(),
                exchangeId
        );

        Thread shareOfferExchangeHandlerThread = new Thread(shareOfferExchangeHandler);
        shareOfferExchangeHandlerThread.setName("ShareOfferExchangeHandler-" + exchangeId);
        shareOfferExchangeHandlerThread.start();

        try {
            shareOfferExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for sharing exchange " + exchangeId + " to complete. Message: " + e.getMessage());
        }

        if (! shareOfferExchangeHandler.isCompleted()) {
            String msg = "ShareOfferExchangeHandler should be completed after awaiting. Aborting sync...";
            logger.error("msg");
            throw new SharingFailedException(msg);
        }

        ShareOfferExchangeHandlerResult offerResult = shareOfferExchangeHandler.getResult();

        if (! offerResult.hasAccepted()) {
            String msg = "Sharing " + exchangeId + " failed. Not all clients have accepted the share offer request. There might be other clients having already a negotiated file id for the file or they are sharing the file too";
            logger.warn(msg);
            throw new SharingFailedException(msg);
        }

        // ok, once we are here, all clients accept the sharing,
        // a negotiated file id is now present

        boolean isFile = true;
        try {
            isFile = this.storageAdapter.isFile(new LocalPathElement(sharingEvent.getRelativePath().toString()));
        } catch (InputOutputException e) {
            String msg = "Can not determine whether the path " + sharingEvent.getRelativePath().toString() + " is a file or directory. Aborting share";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // Now ask all own clients to add the fileId and the sharer to their object store
        SharedExchangeHandler sharedExchangeHandler = new SharedExchangeHandler(
                this.client,
                this.clientManager,
                offerResult.getNegotiatedFileId(),
                sharingEvent.getUsernameToShareWith(),
                sharingEvent.getAccessType(),
                sharingEvent.getRelativePath().toString(),
                isFile,
                exchangeId
        );

        Thread sharedExchangeHandlerThread = new Thread(sharedExchangeHandler);
        sharedExchangeHandlerThread.setName("SharedExchangeHandlerThread-" + exchangeId);
        sharedExchangeHandlerThread.start();

        try {
            sharedExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for all client to respond to the sharedExchange " + exchangeId);
        }

        if (! sharedExchangeHandler.isCompleted()) {
            String msg = "SharedExchangeHandler should be completed after awaiting. Did not send share request to sharer. Own clients may be inconsistent until next sync.";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }


        // own clients did update their object store too, so we can now notify one client of the sharer

        IUser otherUser = new User(sharingEvent.getUsernameToShareWith(), "", "", null, null, new ArrayList<>());
        List<ClientLocation> otherClientsLocations;
        try {
            otherClientsLocations = this.clientManager.getClientLocations(otherUser);
        } catch (InputOutputException e) {
            logger.error("Could not fetch locations from " + sharingEvent.getUsernameToShareWith() + ". Message: " + e.getMessage(), e);
            throw new SharingFailedException("Could not fetch locations from user " + sharingEvent.getUsernameToShareWith() + ". Error: " + e.getMessage());
        }

        if (otherClientsLocations.isEmpty()) {
            String msg = "No client of user " + sharingEvent.getUsernameToShareWith() + " is online. Sharing failed";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        Sharer sharer = new Sharer(sharingEvent.getUsernameToShareWith(), sharingEvent.getAccessType());
        String relativePathToSharedFolder;
        try {
            relativePathToSharedFolder = this.getRelativePathToSharedFolder(sharingEvent.getRelativePath().toString(), sharer);
        } catch (InputOutputException e) {
            logger.error("Can not determine the relative path to the shared folder. We continue with a shared file at the root of the shared directory. Message: " + e.getMessage(), e);
            relativePathToSharedFolder = "";
        }

        ShareExchangeHandler shareExchangeHandler = new ShareExchangeHandler(
                this.client,
                otherClientsLocations.get(0),
                this.storageAdapter,
                sharingEvent.getRelativePath().toString(),
                relativePathToSharedFolder,
                sharer.getAccessType(),
                offerResult.getNegotiatedFileId(),
                isFile,
                exchangeId
        );

        Thread shareExchangeHandlerThread = new Thread(shareExchangeHandler);
        shareExchangeHandlerThread.setName("ShareExchangeHandler-" + exchangeId);
        shareExchangeHandlerThread.start();

        try {
            shareExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for shareExchangeHandler " + exchangeId + " to complete. Message: " + e.getMessage(), e);
        }

        if (! shareExchangeHandler.isCompleted()) {
            String msg = "ShareExchangeHandler should be completed after awaiting. Sync " + exchangeId + " will likely be failed. Aborting";
            logger.error(msg);
            throw new SyncFailedException(msg);
        }
    }

    public void syncUnshareEvent(UnshareEvent sharingEvent) {
        // TODO: negotiate unshare with own clients
        // TODO: send FileUnshareRequest to sharer and FileUnsharedRequest to own clients
    }

    /**
     * Relativizes the given path to its most upper parent which is also shared with the given sharer.
     * Example:
     * Having a given path <i>syncedFolder/aDir/bDir/cDir/myFile.txt</i>, and a most upper shared directory of the
     * given sharer at <i>syncedFolder/aDir/bDir</i>, then this method will return <i>cDir/myFile.txt</i>,
     * so that the file can be placed in the sharers directory at the correct path.
     *
     * @param relativeFilePath The relative path of the file in the synced folder
     * @param sharer           The sharer to check for
     *
     * @return The relativized path
     *
     * @throws InputOutputException If reading the storage adapter / object store failed
     */
    public String getRelativePathToSharedFolder(String relativeFilePath, Sharer sharer)
            throws InputOutputException {
        // look up if there is any parent directory which is also shared with the given path.
        // if so, then we "add" the given file to that directory, resolving the path relatively to that one

        Path path = Paths.get(relativeFilePath);

        while (! relativeFilePath.isEmpty()) {
            Path subPath = path.subpath(0, path.getNameCount());

            IPathElement subPathElement = new LocalPathElement(subPath.toString());
            if (this.storageAdapter.exists(StorageType.DIRECTORY, subPathElement) ||
                    this.storageAdapter.exists(StorageType.FILE, subPathElement)) {
                // check whether the parent is shared
                PathObject parentObject = this.objectStore.getObjectManager().getObjectForPath(subPathElement.getPath());


                if (! parentObject.isShared() || parentObject.getSharers().contains(sharer)) {
                    // parent is not shared with the given shared (or a different access type is present)
                    break;
                }

                // there is a parent which is also shared with the given user
                path = subPath;
            }
        }

        // once we get there, path contains the most upper path which is also shared with the given sharer.
        // Therefore, we can resolve the given relativePath to the most upper one, and will then get
        // the path in the shared folder
        Path relativisedPath = path.relativize(Paths.get(relativeFilePath));

        return relativisedPath.toString();
    }
}
