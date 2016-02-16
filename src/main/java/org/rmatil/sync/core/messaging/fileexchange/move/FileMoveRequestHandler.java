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
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

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

            IPathElement oldPathElement = new LocalPathElement(this.request.getOldPath());
            IPathElement newPathElement = new LocalPathElement(this.request.getNewPath());

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            try {
                if (this.storageAdapter.exists(storageType, oldPathElement)) {
                    this.move(storageType, oldPathElement, newPathElement);
                } else {
                    // TODO: request file on the new path
                }
            } catch (InputOutputException e) {
                logger.error("Could not move path " + this.request.getOldPath() + " to " + this.request.getNewPath() + ". Message: " + e.getMessage());
            }

            this.sendResponse(StatusCode.ACCEPTED);
        } catch (Exception e) {
            logger.error("Error in FileMoveRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Send a response with the given status code back to the requesting client
     *
     * @param statusCode The status code to use in the response
     */
    protected void sendResponse(StatusCode statusCode) {
        this.node.sendDirect(
                this.request.getClientDevice().getPeerAddress(),
                new FileMoveResponse(
                        this.request.getExchangeId(),
                        statusCode,
                        new ClientDevice(
                                this.node.getUser().getUserName(),
                                this.node.getClientDeviceId(),
                                this.node.getPeerAddress()
                        ),
                        new NodeLocation(
                                this.request.getClientDevice().getClientDeviceId(),
                                this.request.getClientDevice().getPeerAddress()
                        )
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
    protected void move(StorageType storageType, IPathElement oldPath, IPathElement newPath)
            throws InputOutputException {

        if (StorageType.DIRECTORY == storageType) {
            try (Stream<Path> paths = Files.walk(this.storageAdapter.getRootDir().resolve(oldPath.getPath()))) {
                paths.forEach((entry) -> {
                    Path oldFilePath = this.storageAdapter.getRootDir().relativize(entry);
                    Path newFilePath = Paths.get(newPath.getPath()).resolve(Paths.get(oldPath.getPath()).relativize(this.storageAdapter.getRootDir().relativize(Paths.get(entry.toString()))));

                    this.globalEventBus.publish(new IgnoreBusEvent(
                            new MoveEvent(
                                    oldFilePath,
                                    newFilePath,
                                    Paths.get(oldPath.getPath()).getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));
                });
            } catch (IOException e) {
                logger.error("Could not create ignore events for the move of " + oldPath.getPath() + ". Message: " + e.getMessage());
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

            this.storageAdapter.move(StorageType.FILE, oldPath, new LocalPathElement(newPath.getPath()));
        }
    }
}
