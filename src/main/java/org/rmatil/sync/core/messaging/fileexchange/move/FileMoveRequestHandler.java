package org.rmatil.sync.core.messaging.fileexchange.move;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Handles incoming {@link FileMoveRequest} and moves the
 * file resp. directory to the specified new path without
 * fetching the file contents again from other peers.
 */
public class FileMoveRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileMoveRequestHandler.class);

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
     * The file move request from the sender
     */
    protected FileMoveRequest request;

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
        if (! (iRequest instanceof FileMoveRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileMoveRequest.class.getName());
        }

        this.request = (FileMoveRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Moving path from " + this.request.getOldPath() + " to " + this.request.getNewPath());

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, this.request.getOldPath())) {
                logger.warn("Moving path failed due to missing access rights on file " + this.request.getOldPath() + " for user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(StatusCode.ACCESS_DENIED);
                return;
            }

            TreePathElement oldPathElement = new TreePathElement(this.request.getOldPath());
            TreePathElement newPathElement = new TreePathElement(this.request.getNewPath());

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            try {
                // if the file does not exist, do not move and rely on the background syncer
                // which will fetch it
                if (this.storageAdapter.exists(storageType, oldPathElement)) {
                    this.move(storageType, oldPathElement, newPathElement);
                }
            } catch (InputOutputException e) {
                logger.error("Could not move path " + this.request.getOldPath() + " to " + this.request.getNewPath() + ". Message: " + e.getMessage());
            }

            this.sendResponse(StatusCode.ACCEPTED);
        } catch (Exception e) {
            logger.error("Error in FileMoveRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);

            try {
                this.sendResponse(StatusCode.ERROR);
            } catch (Exception e1) {
                logger.error("Failed to notify originating node about error in exchange " + this.request.getExchangeId() + ". Message: " + e1.getMessage(), e1);
            }
        }
    }

    /**
     * Send a response with the given status code back to the requesting client
     *
     * @param statusCode The status code to use in the response
     */
    protected void sendResponse(StatusCode statusCode) {
        NodeLocation receiver = new NodeLocation(
                this.request.getClientDevice().getUserName(),
                this.request.getClientDevice().getClientDeviceId(),
                this.request.getClientDevice().getPeerAddress()
        );

        this.node.sendDirect(
                receiver,
                new FileMoveResponse(
                        this.request.getExchangeId(),
                        statusCode,
                        new ClientDevice(
                                this.node.getUser().getUserName(),
                                this.node.getClientDeviceId(),
                                this.node.getPeerAddress()
                        ),
                        receiver
                )
        );
    }

    /**
     * Moves the specified element from oldPath > newPath
     *
     * @param storageType The storage type of the element to move
     * @param oldPath     The old path to the element
     * @param newPath     The new path to which the element should be moved
     *
     * @throws InputOutputException If moving failed
     */
    protected void move(StorageType storageType, TreePathElement oldPath, TreePathElement newPath)
            throws InputOutputException {

        if (StorageType.DIRECTORY == storageType) {
            List<TreePathElement> elementsToIgnore = new ArrayList<>();
            elementsToIgnore.add(oldPath);

            if (this.storageAdapter.isDir(oldPath)) {
                // add all directory contents to the ignored files too
                elementsToIgnore.addAll(this.storageAdapter.getDirectoryContents(oldPath));
            }

            for (TreePathElement element : elementsToIgnore) {
                Path oldFilePath = Paths.get(element.getPath());
                Path newFilePath = Paths.get(newPath.getPath()).resolve(Paths.get(oldPath.getPath()).relativize(Paths.get(this.storageAdapter.getRootDir().getPath()).relativize(Paths.get(element.getPath().toString()))));

                this.globalEventBus.publish(new IgnoreBusEvent(
                        new MoveEvent(
                                oldFilePath,
                                newFilePath,
                                Paths.get(oldPath.getPath()).getFileName().toString(),
                                "weIgnoreTheHash",
                                System.currentTimeMillis()
                        )
                ));
            }

            this.storageAdapter.move(storageType, oldPath, newPath);
        } else {
            PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(oldPath.getPath());
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new MoveEvent(
                            Paths.get(oldPath.getPath()),
                            Paths.get(newPath.getPath()),
                            Paths.get(newPath.getPath()).getFileName().toString(),
                            pathObject.getVersions().get(Math.max(pathObject.getVersions().size() - 1, 0)).getHash(),
                            System.currentTimeMillis()
                    )
            ));

            this.storageAdapter.move(StorageType.FILE, oldPath, new TreePathElement(newPath.getPath()));
        }
    }
}
