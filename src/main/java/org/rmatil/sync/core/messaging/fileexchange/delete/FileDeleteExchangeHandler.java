package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
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

/**
 * Propagates delete events to other connected clients.
 *
 * @see FileDeleteRequestHandler
 */
public class FileDeleteExchangeHandler extends ANetworkHandler<FileDeleteExchangeHandlerResult> implements ILocalStateResponseCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    /**
     * The exchangeId for this delete exchange
     */
    protected UUID exchangeId;

    /**
     * The client device which is sending the request
     */
    protected ClientDevice clientDevice;

    /**
     * The storage adapter to access the synced folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The client manager to get the client locations from
     */
    protected INodeManager nodeManager;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The actual delete event to propagate
     * to the other clients
     */
    protected DeleteEvent deleteEvent;

    /**
     * The global event bus
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The client location to send the requests to
     */
    protected List<NodeLocation> receivers;

    /**
     * @param exchangeId     The exchange id for this exchange
     * @param clientDevice   The client device from the client starting the exchange
     * @param storageAdapter The storage adapter to access the synced folder
     * @param nodeManager    The client manager to access client locations
     * @param client         The client to send the actual message
     * @param objectStore    The object store
     * @param globalEventBus The global event bus to send events to
     * @param receivers      The receiver addresses which should receive the delete requests
     * @param deleteEvent    The actual delete event to propagate
     */
    public FileDeleteExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, INodeManager nodeManager, INode client, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, List<NodeLocation> receivers, DeleteEvent deleteEvent) {
        super(client);
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.nodeManager = nodeManager;
        this.globalEventBus = globalEventBus;
        this.objectStore = objectStore;
        this.receivers = receivers;
        this.deleteEvent = deleteEvent;
    }

    @Override
    public void run() {
        try {
            List<PathObject> deletedPaths = this.objectStore.getObjectManager().getChildren(this.deleteEvent.getPath().toString());

            // ignore delete events from children
            for (PathObject entry : deletedPaths) {
                Path filePathToDelete = Paths.get(entry.getAbsolutePath());

                // remove the fileId
                this.node.getIdentifierManager().removeIdentifier(filePathToDelete.toString());

                this.globalEventBus.publish(
                        new IgnoreBusEvent(
                                new DeleteEvent(
                                        filePathToDelete,
                                        entry.getName(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        )
                );
            }

            PathObject deletedPath = this.objectStore.getObjectManager().getObjectForPath(this.deleteEvent.getPath().toString());

            String owner = null;
            UUID fileId = null;
            if (null != deletedPath.getOwner() &&
                    ! this.node.getUser().getUserName().equals(deletedPath.getOwner()) &&
                    AccessType.WRITE.equals(deletedPath.getAccessType())) {
                // we got write permissions, so we send the changes also back to the original owner of the file
                try {
                    fileId = super.node.getIdentifierManager().getValue(deletedPath.getAbsolutePath());
                    owner = deletedPath.getOwner();

                } catch (InputOutputException e) {
                    logger.error("Could not fetch file id for file " + deletedPath.getAbsolutePath() + ". Message: " + e.getMessage());
                }
            }

            // add file id also if the path is shared
            if (deletedPath.isShared()) {
                for (Sharer entry : deletedPath.getSharers()) {
                    try {
                        // ask sharer's clients to get the changes too
                        List<NodeLocation> sharerLocations = this.nodeManager.getNodeLocations(entry.getUsername());

                        // only add one client of the sharer. He may propagate the change then
                        // to his clients, and if a conflict occurs, there will be a new file
                        if (! sharerLocations.isEmpty()) {
                            fileId = super.node.getIdentifierManager().getValue(deletedPath.getAbsolutePath());
                            this.receivers.add(sharerLocations.get(0));
                        }
                    } catch (InputOutputException e) {
                        logger.error("Could not get client locations of sharer " + entry.getUsername() + ". Skipping this sharer's clients");
                    }
                }
            }

            logger.info("Sending delete request " + this.exchangeId);

            FileDeleteRequest fileDeleteRequest = new FileDeleteRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    this.clientDevice,
                    fileId,
                    owner,
                    this.receivers,
                    this.deleteEvent.getPath().toString()
            );

            super.sendRequest(fileDeleteRequest);

        } catch (Exception e) {
            logger.error("Failed to execute FileDeleteExchange. Message: " + e.getMessage(), e);
        }

    }

    @Override
    public List<String> getAffectedFilePaths() {
        List<String> affectedFiles = new ArrayList<>();
        affectedFiles.add(this.deleteEvent.getPath().toString());
        return affectedFiles;
    }

    @Override
    public FileDeleteExchangeHandlerResult getResult() {
        return new FileDeleteExchangeHandlerResult();
    }
}
