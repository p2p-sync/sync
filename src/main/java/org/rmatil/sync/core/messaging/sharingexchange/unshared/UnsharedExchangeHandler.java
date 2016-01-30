package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class UnsharedExchangeHandler extends ANetworkHandler<UnsharedExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(UnsharedExchangeHandler.class);

    protected IClientManager clientManager;

    protected IObjectStore objectStore;

    protected String relativeFilePath;

    protected UUID fileId;

    protected String sharer;

    protected UUID exchangeId;

    public UnsharedExchangeHandler(IClient client, IClientManager clientManager, IObjectStore objectStore, String relativeFilePath, UUID fileId, String sharer, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.objectStore = objectStore;
        this.relativeFilePath = relativeFilePath;
        this.fileId = fileId;
        this.sharer = sharer;
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
                    this.sharer,
                    this.fileId
            );

            // remove sharer from the file
            this.objectStore.getSharerManager().removeSharer(
                    this.sharer,
                    this.relativeFilePath
            );

            super.sendRequest(unsharedRequest);

        } catch (Exception e) {
            logger.error("Got exception in UnsharedExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public UnsharedExchangeHandlerResult getResult() {
        return new UnsharedExchangeHandlerResult();
    }
}
