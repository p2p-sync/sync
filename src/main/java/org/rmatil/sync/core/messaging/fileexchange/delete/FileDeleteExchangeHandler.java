package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveRequest;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FileDeleteExchangeHandler extends ANetworkHandler<FileDeleteExchangeHandlerResult> implements ILocalStateResponseCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    protected UUID exchangeId;

    protected ClientDevice clientDevice;

    protected IStorageAdapter storageAdapter;

    protected IClientManager clientManager;

    protected IObjectStore objectStore;

    protected DeleteEvent deleteEvent;

    protected MBassador<IBusEvent> globalEventBus;

    public FileDeleteExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IClientManager clientManager, IClient client, IObjectStore objectStore, MBassador<IBusEvent> globalEventBus, DeleteEvent deleteEvent) {
        super(client);
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
        this.globalEventBus = globalEventBus;
        this.objectStore = objectStore;
        this.deleteEvent = deleteEvent;
    }

    @Override
    public void run() {
        try {
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            List<PathObject> deletedPaths = this.objectStore.getObjectManager().getChildren(this.deleteEvent.getPath().toString());

            // ignore delete events from children
            for (PathObject entry : deletedPaths) {
                this.globalEventBus.publish(
                        new IgnoreBusEvent(
                                new DeleteEvent(
                                        Paths.get(entry.getAbsolutePath()),
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
                    clientLocations,
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
    public void onResponse(IResponse iResponse) {
        // Currently, we do not handle a response of a delete exchange
        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public FileDeleteExchangeHandlerResult getResult() {
        return new FileDeleteExchangeHandlerResult();
    }
}
