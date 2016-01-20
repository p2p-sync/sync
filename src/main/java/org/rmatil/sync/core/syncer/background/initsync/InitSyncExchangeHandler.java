package org.rmatil.sync.core.syncer.background.initsync;

import org.rmatil.sync.core.syncer.background.BackgroundSyncer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Initializes the actual reconciliation of the
 * object stores. This stops the event aggregator
 * on all clients, including the client which initiates
 * sync.
 *
 * @see BackgroundSyncer
 */
public class InitSyncExchangeHandler extends ANetworkHandler<InitSyncExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(InitSyncExchangeHandler.class);

    /**
     * The client manager to fetch all client locations from
     */
    protected IClientManager clientManager;

    /**
     * The elected master
     */
    protected ClientDevice electedMaster;

    /**
     * The event aggregator to stop
     */
    protected IEventAggregator eventAggregator;

    /**
     * The exchange id for the sync initialisation
     */
    protected UUID exchangeId;

    /**
     * @param client          The client to send messages
     * @param clientManager   The client manager to fetch client locations from
     * @param eventAggregator The event aggregator to stop
     * @param exchangeId      The exchange id used for the initialisation
     * @param electedMaster   The elected master
     */
    public InitSyncExchangeHandler(IClient client, IClientManager clientManager, IEventAggregator eventAggregator, UUID exchangeId, ClientDevice electedMaster) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
        this.electedMaster = electedMaster;
        this.eventAggregator = eventAggregator;
    }

    @Override
    public void run() {
        try {
            logger.info("Stopping event aggregator on client (" + this.client.getPeerAddress().inetAddress().getHostName() + ":" + this.client.getPeerAddress().tcpPort() + ")");
            this.eventAggregator.stop();

            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            super.sendRequest(new InitSyncRequest(
                    this.exchangeId,
                    new ClientDevice(super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    clientLocations,
                    this.electedMaster
            ));

        } catch (Exception e) {
            logger.error("Got exception in InitSyncExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        // currently, we ignore the result of such a response
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public InitSyncExchangeHandlerResult getResult() {
        return new InitSyncExchangeHandlerResult();
    }
}
