package org.rmatil.sync.core.messaging.fileexchange.move;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class FileMoveExchangeHandler extends ANetworkHandler<FileMoveExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileMoveExchangeHandler.class);

    protected UUID exchangeId;

    protected ClientDevice clientDevice;

    protected IStorageAdapter storageAdapter;

    protected IClientManager clientManager;

    protected MBassador<IBusEvent> globalEventBus;

    protected MoveEvent moveEvent;

    public FileMoveExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IClientManager clientManager, IClient client, MBassador<IBusEvent> globalEventBus, MoveEvent moveEvent) {
        super(client);
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
        this.globalEventBus = globalEventBus;
        this.moveEvent = moveEvent;
    }

    @Override
    public void run() {
        try {
            boolean isFile = this.storageAdapter.isFile(new LocalPathElement(this.moveEvent.getNewPath().toString()));

            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            for (ClientLocation location : clientLocations) {
                UUID uuid = UUID.randomUUID();
                logger.info("Sending move request as subRequest of " + this.exchangeId + " with id " + uuid + " to client " + location.getPeerAddress().inetAddress().getHostName() + ":" + location.getPeerAddress().tcpPort());
                // add callback handler for subrequest
                super.client.getObjectDataReplyHandler().addResponseCallbackHandler(uuid, this);

                FileMoveRequest fileMoveRequest = new FileMoveRequest(
                        uuid,
                        this.clientDevice,
                        location,
                        this.moveEvent.getPath().toString(),
                        this.moveEvent.getNewPath().toString(),
                        isFile
                );

                super.client.getObjectDataReplyHandler().addResponseCallbackHandler(uuid, this);

                super.sendRequest(fileMoveRequest);
            }
        } catch (Exception e) {
            logger.error("Failed to execute FileMoveExchange. Message: " + e.getMessage());
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        // Currently, we do not handle a response of a move exchange
        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public FileMoveExchangeHandlerResult getResult() {
        return new FileMoveExchangeHandlerResult();
    }
}
