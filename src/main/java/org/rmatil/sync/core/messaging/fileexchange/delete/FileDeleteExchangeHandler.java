package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
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

public class FileDeleteExchangeHandler extends ANetworkHandler<FileDeleteExchangeHandlerResult> implements ILocalStateResponseCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    protected UUID exchangeId;

    protected ClientDevice clientDevice;

    protected IStorageAdapter storageAdapter;

    protected IClientManager clientManager;

    protected IObjectStore objectStore;

    protected DeleteEvent deleteEvent;

    protected MBassador<IBusEvent> globalEventBus;

    protected List<ClientLocation> receivers;

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
