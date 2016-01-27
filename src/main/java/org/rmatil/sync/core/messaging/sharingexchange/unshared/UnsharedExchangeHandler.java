package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.sharingexchange.unshare.UnshareRequest;
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

public class UnsharedExchangeHandler extends ANetworkHandler<UnsharedExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(UnsharedExchangeHandler.class);

    protected IClientManager clientManager;

    protected UUID fileId;

    protected UUID exchangeId;

    public UnsharedExchangeHandler(IClient client, IClientManager clientManager, UUID fileId, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.fileId = fileId;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            logger.info("Sending unshare request for own clients. Exchange " + this.exchangeId);

            // Fetch client locations from the DHT
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }


            UnsharedRequest unsharedRequest = new UnsharedRequest(
                    this.exchangeId,
                    new ClientDevice(
                            super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    clientLocations,
                    this.fileId
            );

            super.sendRequest(unsharedRequest);

        } catch (Exception e) {
            logger.error("Got exception in UnsharedExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        logger.info("Received response for exchange " + response.getExchangeId() + " of client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public UnsharedExchangeHandlerResult getResult() {
        return new UnsharedExchangeHandlerResult();
    }
}
