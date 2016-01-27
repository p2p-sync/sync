package org.rmatil.sync.core.messaging.sharingexchange.offer;

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

public class ShareOfferExchangeHandler extends ANetworkHandler<ShareOfferExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(ShareOfferExchangeHandler.class);

    protected IClientManager clientManager;

    protected String pathToShare;

    protected UUID exchangeId;

    protected List<ShareOfferResponse> respondedClients;

    protected UUID proposedFileId;

    public ShareOfferExchangeHandler(IClient client, IClientManager clientManager, String pathToShare, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.pathToShare = pathToShare;
        this.exchangeId = exchangeId;
        this.proposedFileId = UUID.randomUUID();
        this.respondedClients = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            logger.info("Starting ShareOfferExchange " + this.exchangeId + " and proposing unique fileId " + this.proposedFileId);

            // Fetch client locations from the DHT
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            ShareOfferRequest shareOfferRequest = new ShareOfferRequest(
                    this.exchangeId,
                    new ClientDevice(
                            super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    clientLocations,
                    this.proposedFileId,
                    this.pathToShare
            );

            super.sendRequest(shareOfferRequest);
        } catch (Exception e) {
            logger.error("Got exception in ShareOfferExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        logger.info("Received response for exchange " + response.getExchangeId() + " of client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")");

        if (! (response instanceof ShareOfferResponse)) {
            logger.error("Received response " + response.getClass().getName() + " but excepted " + ShareOfferResponse.class.getName());
            return;
        }

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        this.respondedClients.add((ShareOfferResponse) response);
        super.countDownLatch.countDown();
    }

    @Override
    public ShareOfferExchangeHandlerResult getResult() {
        for (ShareOfferResponse response : this.respondedClients) {
            if (! response.hasAccepted()) {
                return new ShareOfferExchangeHandlerResult(false, null);
            }
        }

        return new ShareOfferExchangeHandlerResult(true, this.proposedFileId);
    }
}
