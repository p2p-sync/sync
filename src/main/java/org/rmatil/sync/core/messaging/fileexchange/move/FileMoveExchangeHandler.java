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
        // TODO: traverse directory and ignore all move events of its contents
        try {
            boolean isFile = this.storageAdapter.isFile(new LocalPathElement(this.moveEvent.getNewPath().toString()));

            // since this sync is triggered by a move, the actual operation is already
            // done on this client, therefore we traverse the dir on the new path
            if (! isFile) {
                Path dirToMove = this.storageAdapter.getRootDir().resolve(this.moveEvent.getNewPath());
                try (Stream<Path> paths = Files.walk(dirToMove)) {
                    paths.forEach((entry) -> {
                        Path relPath = this.storageAdapter.getRootDir().toAbsolutePath().relativize(entry);
                        Path oldPath = this.moveEvent.getPath().resolve(this.moveEvent.getNewPath().relativize(relPath));

                        globalEventBus.publish(new IgnoreBusEvent(
                                new MoveEvent(
                                        oldPath,
                                        entry,
                                        entry.getFileName().toString(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        ));
                    });
                } catch (IOException e) {
                    logger.error("Could not create ignore events for moving " + this.moveEvent.getPath().toString() + " to " + this.moveEvent.getNewPath().toString() + ". Message: " + e.getMessage());
                }
            }

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
        super.countDownLatch.countDown();
    }

    @Override
    public FileMoveExchangeHandlerResult getResult() {
        return new FileMoveExchangeHandlerResult();
    }
}
