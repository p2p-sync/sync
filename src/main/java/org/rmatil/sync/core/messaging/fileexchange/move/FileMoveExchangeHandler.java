package org.rmatil.sync.core.messaging.fileexchange.move;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FileMoveExchangeHandler extends ANetworkHandler<FileMoveExchangeHandlerResult> implements ILocalStateResponseCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileMoveExchangeHandler.class);

    protected UUID exchangeId;

    protected ClientDevice clientDevice;

    protected IStorageAdapter storageAdapter;

    protected IClientManager clientManager;

    protected MBassador<IBusEvent> globalEventBus;

    protected MoveEvent moveEvent;

    protected CountDownLatch moveCountDownLatch;

    protected List<ClientLocation> receivers;

    public FileMoveExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IClientManager clientManager, IClient client, MBassador<IBusEvent> globalEventBus, List<ClientLocation> receivers, MoveEvent moveEvent) {
        super(client);
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.clientManager = clientManager;
        this.globalEventBus = globalEventBus;
        this.receivers = receivers;
        this.moveEvent = moveEvent;
    }

    @Override
    public void run() {
        try {
            boolean isFile = this.storageAdapter.isFile(new LocalPathElement(this.moveEvent.getNewPath().toString()));

            // check whether the own client is also in the list (should be usually, but you never know...)
            int clientCounter = this.receivers.size();
            for (ClientLocation location : this.receivers) {
                if (location.getPeerAddress().equals(this.client.getPeerAddress())) {
                    clientCounter--;
                    break;
                }
            }

            this.moveCountDownLatch = new CountDownLatch(clientCounter);

            for (ClientLocation location : this.receivers) {
                UUID uuid = UUID.randomUUID();
                logger.info("Sending move request as subRequest of " + this.exchangeId + " with id " + uuid + " to client " + location.getPeerAddress().inetAddress().getHostName() + ":" + location.getPeerAddress().tcpPort());
                // add callback handler for subrequest
                super.client.getObjectDataReplyHandler().addResponseCallbackHandler(uuid, this);

                FileMoveRequest fileMoveRequest = new FileMoveRequest(
                        uuid,
                        StatusCode.NONE,
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
    public List<String> getAffectedFilePaths() {
        List<String> affectedFiles = new ArrayList<>();

        affectedFiles.add(this.moveEvent.getPath().toString());
        affectedFiles.add(this.moveEvent.getNewPath().toString());

        return affectedFiles;
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof FileMoveResponse)) {
            logger.error("Expected response to be instance of " + FileMoveResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        super.onResponse(response);

        this.moveCountDownLatch.countDown();
    }

    @Override
    public FileMoveExchangeHandlerResult getResult() {
        return new FileMoveExchangeHandlerResult();
    }

    @Override
    public void await()
            throws InterruptedException {
        super.await();
        this.moveCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        super.await();
        this.moveCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isCompleted() {
        return super.isCompleted() && null != this.moveCountDownLatch && 0L == this.moveCountDownLatch.getCount();
    }
}
