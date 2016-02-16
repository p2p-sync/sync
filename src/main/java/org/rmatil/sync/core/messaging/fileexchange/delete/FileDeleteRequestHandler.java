package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * The request handler for a FileDeleteExchange.
 *
 * @see FileDeleteExchangeHandler
 */
public class FileDeleteRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    /**
     * The storage adapter to access the synced folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The client to send responses
     */
    protected INode node;

    /**
     * The file delete request which have been received
     */
    protected FileDeleteRequest request;

    /**
     * The global event bus to send events to
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
    public void setNode(INode node) {
        this.node = node;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest request) {
        if (! (request instanceof FileDeleteRequest)) {
            throw new IllegalArgumentException("Got request " + request.getClass().getName() + " but expected " + FileDeleteExchangeHandler.class.getName());
        }

        this.request = (FileDeleteRequest) request;
    }

    @Override
    public void run() {
        try {
            logger.info("Deleting path on " + this.request.getPathToDelete());

            if (! this.node.getUser().getUserName().equals(this.request.getClientDevice().getUserName()) && ! this.accessManager.hasAccess(this.request.getClientDevice().getUserName(), AccessType.WRITE, this.request.getPathToDelete())) {
                // client has no access to delete the file
                logger.warn("Deletion failed due to missing access rights on file " + this.request.getPathToDelete() + " for user " + this.request.getClientDevice().getUserName() + " on exchange " + this.request.getExchangeId());
                this.sendResponse(StatusCode.ACCESS_DENIED);
                return;
            }

            IPathElement pathToDelete = new LocalPathElement(this.request.getPathToDelete());
            try {
                if (this.storageAdapter.exists(StorageType.DIRECTORY, pathToDelete) || this.storageAdapter.exists(StorageType.FILE, pathToDelete)) {
                    // create ignore events for all dir contents
                    try (Stream<Path> paths = Files.walk(this.storageAdapter.getRootDir().resolve(pathToDelete.getPath()))) {
                        paths.forEach((entry) -> this.globalEventBus.publish(new IgnoreBusEvent(
                                new DeleteEvent(
                                        this.storageAdapter.getRootDir().relativize(entry),
                                        this.storageAdapter.getRootDir().relativize(entry).getFileName().toString(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        )));
                    } catch (IOException e) {
                        logger.error("Could not create ignore events for the deletion of " + this.request.getPathToDelete() + ". Message: " + e.getMessage());
                    }

                    this.storageAdapter.delete(pathToDelete);
                }
            } catch (InputOutputException e) {
                logger.error("Could not delete path " + pathToDelete.getPath() + ". Message: " + e.getMessage());
            }

            this.sendResponse(StatusCode.ACCEPTED);

        } catch (Exception e) {
            logger.error("Error in FileDeleteRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a response with the given status code back to the sender
     *
     * @param statusCode The status code to use in the response
     */
    protected void sendResponse(StatusCode statusCode) {
        this.node.sendDirect(
                this.request.getClientDevice().getPeerAddress(),
                new FileDeleteResponse(
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
}
