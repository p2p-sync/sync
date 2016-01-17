package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FetchObjectStoreExchangeHandler extends ANetworkHandler<FetchObjectStoreExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FetchObjectStoreExchangeHandler.class);

    protected IClientManager clientManager;

    protected UUID exchangeId;

    protected List<FetchObjectStoreResponse> responses;

    public FetchObjectStoreExchangeHandler(IClient client, IClientManager clientManager, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
        this.responses = new ArrayList<>();
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

            FetchObjectStoreRequest syncObjectStoreRequest = new FetchObjectStoreRequest(
                    this.exchangeId,
                    new ClientDevice(super.client.getUser().getUserName(), super.client.getClientDeviceId(), super.client.getPeerAddress()),
                    clientLocations
            );

            super.sendRequest(syncObjectStoreRequest);

        } catch (Exception e) {
            logger.error("Got exception in SyncObjectStoreExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        this.responses.add((FetchObjectStoreResponse) iResponse);
        super.countDownLatch.countDown();
    }

    @Override
    public FetchObjectStoreExchangeHandlerResult getResult() {
        return new FetchObjectStoreExchangeHandlerResult(this.responses);
    }
}
