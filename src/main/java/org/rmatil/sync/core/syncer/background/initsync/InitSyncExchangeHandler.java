package org.rmatil.sync.core.syncer.background.initsync;

import org.rmatil.sync.core.syncer.background.BlockingBackgroundSyncer;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Initializes the actual reconciliation of the
 * object stores. This stops the event aggregator
 * on all clients, including the client which initiates
 * sync.
 *
 * @see BlockingBackgroundSyncer
 *
 * @deprecated As of 0.1. Will be removed in future releases.
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
    public InitSyncExchangeHandlerResult getResult() {
        return new InitSyncExchangeHandlerResult();
    }
}
