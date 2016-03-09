package org.rmatil.sync.core.syncer.sharing;

import org.rmatil.sync.core.ShareNaming;
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
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class SharingSyncer implements ISharingSyncer {

    private static final Logger logger = LoggerFactory.getLogger(SharingSyncer.class);

    protected INode               node;
    protected INodeManager        nodeManager;
    protected ITreeStorageAdapter storageAdapter;
    protected IObjectStore        objectStore;


    public SharingSyncer(INode node, INodeManager nodeManager, ITreeStorageAdapter storageAdapter, IObjectStore objectStore) {
        this.node = node;
        this.nodeManager = nodeManager;
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

        if (this.node.getUser().getUserName().equals(sharingEvent.getUsernameToShareWith())) {
            throw new SharingFailedException("Sharing with the own user is not permitted");
        }

        UUID exchangeId = UUID.randomUUID();

        UUID fileId;
        try {
            fileId = this.node.getIdentifierManager().getValue(sharingEvent.getRelativePath().toString());
        } catch (InputOutputException e) {
            String msg = "Could not find fileId for file " + sharingEvent.getRelativePath().toString() + ". Message: " + e.getMessage();
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        boolean isFile = true;
        try {
            isFile = this.storageAdapter.isFile(new TreePathElement(sharingEvent.getRelativePath().toString()));
        } catch (InputOutputException e) {
            String msg = "Can not determine whether the path " + sharingEvent.getRelativePath().toString() + " is a file or directory. Aborting share";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // avoid sharing on our side too, if no client of the sharer is connected
        NodeLocation sharerLocation = this.getClientLocationFromSharer(sharingEvent.getUsernameToShareWith());
        if (null == sharerLocation) {
            String msg = "No client of user " + sharingEvent.getUsernameToShareWith() + " is online. Sharing failed";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // Now ask all own clients to add the sharer to their object store
        SharedExchangeHandler sharedExchangeHandler = new SharedExchangeHandler(
                this.node,
                this.nodeManager,
                this.objectStore,
                sharingEvent.getUsernameToShareWith(),
                sharingEvent.getAccessType(),
                sharingEvent.getRelativePath().toString(),
                exchangeId
        );

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, sharedExchangeHandler);

        Thread sharedExchangeHandlerThread = new Thread(sharedExchangeHandler);
        sharedExchangeHandlerThread.setName("SharedExchangeHandlerThread-" + exchangeId);
        sharedExchangeHandlerThread.start();

        try {
            sharedExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for all client to respond to the sharedExchange " + exchangeId);
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (! sharedExchangeHandler.isCompleted()) {
            String msg = "SharedExchangeHandler should be completed after awaiting. Did not send share request to sharer. Own clients may be inconsistent until next sync.";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // own clients did update their object store too, so we can now notify one client of the sharer

        String relativePathToSharedFolder;
        try {
            relativePathToSharedFolder = ShareNaming.getRelativePathToSharedFolderBySharer(this.storageAdapter, this.objectStore, sharingEvent.getRelativePath().toString(), sharingEvent.getUsernameToShareWith(), sharingEvent.getAccessType());
        } catch (InputOutputException e) {
            logger.error("Can not determine the relative path to the shared folder. We continue with a shared file at the root of the shared directory. Message: " + e.getMessage(), e);
            relativePathToSharedFolder = "";
        }

        ShareExchangeHandler shareExchangeHandler = new ShareExchangeHandler(
                this.node,
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

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, shareExchangeHandler);

        Thread shareExchangeHandlerThread = new Thread(shareExchangeHandler);
        shareExchangeHandlerThread.setName("ShareExchangeHandler-" + exchangeId);
        shareExchangeHandlerThread.start();

        try {
            shareExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for shareExchangeHandler " + exchangeId + " to complete. Message: " + e.getMessage(), e);
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

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

        if (this.node.getUser().getUserName().equals(unshareEvent.getUsernameToShareWith())) {
            throw new UnsharingFailedException("Unsharing with the own user is not permitted");
        }

        UUID exchangeId = UUID.randomUUID();

        UUID fileId;
        try {
            fileId = this.node.getIdentifierManager().getValue(unshareEvent.getRelativePath().toString());
        } catch (InputOutputException e) {
            String msg = "Could not fetch fileId for file " + unshareEvent.getRelativePath().toString() + ". Message: " + e.getMessage();
            logger.error(msg, e);
            throw new UnsharingFailedException(msg, e);
        }

        // avoid unsharing on our side if no client of the sharer is connected
        NodeLocation sharerLocation = this.getClientLocationFromSharer(unshareEvent.getUsernameToShareWith());
        if (null == sharerLocation) {
            String msg = "No client of user " + unshareEvent.getUsernameToShareWith() + " is online. Sharing failed";
            logger.error(msg);
            throw new SharingFailedException(msg);
        }

        // unshare on own clients
        UnsharedExchangeHandler unsharedExchangeHandler = new UnsharedExchangeHandler(
                this.node,
                this.nodeManager,
                this.objectStore,
                unshareEvent.getRelativePath().toString(),
                fileId,
                unshareEvent.getUsernameToShareWith(),
                exchangeId
        );

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, unsharedExchangeHandler);

        Thread unsharedExchangeHandlerThread = new Thread(unsharedExchangeHandler);
        unsharedExchangeHandlerThread.setName("UnsharedExchangeHandler-" + exchangeId);
        unsharedExchangeHandlerThread.start();

        try {
            unsharedExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for unsharing " + exchangeId + " to complete on own clients. Message: " + e.getMessage(), e);
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (! unsharedExchangeHandler.isCompleted()) {
            String msg = "UnsharedExchangeHandler should be completed after awaiting. Unsharing might not be complete on own clients. Aborting";
            logger.error(msg);
            throw new UnsharingFailedException(msg);
        }

        // unshare with sharer
        UnshareExchangeHandler unshareExchangeHandler = new UnshareExchangeHandler(
                this.node,
                sharerLocation,
                fileId,
                exchangeId
        );

        this.node.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, unshareExchangeHandler);

        Thread unshareExchangeHandlerThread = new Thread(unshareExchangeHandler);
        unshareExchangeHandlerThread.setName("UnshareExchangeHandler-" + exchangeId);
        unshareExchangeHandlerThread.start();

        try {
            unshareExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for unsharing " + exchangeId + " to complete on own clients. Message: " + e.getMessage(), e);
        }

        this.node.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

        if (! unshareExchangeHandler.isCompleted()) {
            String msg = "UnshareExchangeHandler should be completed after awaiting. Unsharing might not be complete on sharer's clients";
            logger.error(msg);
            throw new UnsharingFailedException(msg);
        }

        logger.info("Completed unsharing of file " + unshareEvent.getRelativePath() + " with user " + unshareEvent.getUsernameToShareWith() + " (" + unshareEvent.getAccessType() + ")");
    }

    /**
     * Returns a single client location from the given sharer
     *
     * @param sharer The sharer of which to get the client location
     *
     * @return The found client location
     */
    public NodeLocation getClientLocationFromSharer(String sharer)
            throws SharingFailedException {
        List<NodeLocation> otherClientsLocations;
        try {
            otherClientsLocations = this.nodeManager.getNodeLocations(sharer);
        } catch (InputOutputException e) {
            logger.error("Could not fetch locations from " + sharer + ". Message: " + e.getMessage(), e);
            throw new SharingFailedException("Could not fetch locations from user " + sharer + ". Error: " + e.getMessage());
        }

        if (otherClientsLocations.isEmpty()) {
            return null;
        }

        return otherClientsLocations.get(0);
    }
}
