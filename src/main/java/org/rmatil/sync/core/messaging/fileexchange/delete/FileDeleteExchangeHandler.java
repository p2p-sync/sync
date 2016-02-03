package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
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
    protected IClientManager clientManager;

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
    protected List<ClientLocation> receivers;

    /**
     * @param exchangeId     The exchange id for this exchange
     * @param clientDevice   The client device from the client starting the exchange
     * @param storageAdapter The storage adapter to access the synced folder
     * @param clientManager  The client manager to access client locations
     * @param client         The client to send the actual message
     * @param objectStore    The object store
     * @param globalEventBus The global event bus to send events to
     * @param receivers      The receiver addresses which should receive the delete requests
     * @param deleteEvent    The actual delete event to propagate
     */
    public FileDeleteExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IClientManager clientManager, IClient client, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, List<ClientLocation> receivers, DeleteEvent deleteEvent) {
        super(client);
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
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
                this.client.getIdentifierManager().removeIdentifier(filePathToDelete.toString());

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

            logger.info("Sending delete request " + this.exchangeId);

            FileDeleteRequest fileDeleteRequest = new FileDeleteRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    this.clientDevice,
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
