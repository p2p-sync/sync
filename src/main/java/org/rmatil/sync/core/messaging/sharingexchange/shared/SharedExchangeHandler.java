package org.rmatil.sync.core.messaging.sharingexchange.shared;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectManager;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SharedExchangeHandler extends ANetworkHandler<SharedExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SharedExchangeHandler.class);

    protected IClientManager clientManager;

    protected IObjectStore objectStore;

    protected String sharer;

    protected AccessType accessType;

    protected String relativeFilePath;

    protected List<SharedResponse> respondedClients;

    protected UUID exchangeId;

    public SharedExchangeHandler(IClient client, IClientManager clientManager, IObjectStore objectStore, String sharer, AccessType accessType, String relativeFilePath, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.objectStore = objectStore;
        this.sharer = sharer;
        this.accessType = accessType;
        this.relativeFilePath = relativeFilePath;
        this.exchangeId = exchangeId;
        this.respondedClients = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            // Fetch client locations from the DHT
            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            // exchange file id and sharer among all clients
            SharedRequest request = new SharedRequest(
                    this.exchangeId,
                    new ClientDevice(
                            super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    clientLocations,
                    this.sharer,
                    this.accessType,
                    this.relativeFilePath
            );

            super.sendRequest(request);

            // add sharer to the file
            this.objectStore.getSharerManager().addSharer(
                    this.sharer,
                    this.accessType,
                    this.relativeFilePath
            );

            // if there is no owner of the file yet (due to sharing already, or
            // if we are not the owner, but just share the shared file with any other user)
            // set it to our self
            if (null == this.objectStore.getSharerManager().getOwner(this.relativeFilePath)) {
                this.objectStore.getSharerManager().addOwner(
                        this.client.getUser().getUserName(),
                        this.relativeFilePath
                );
            }

        } catch (Exception e) {
            logger.error("Got exception in SharedExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof SharedResponse)) {
            logger.error("Expected response to be instance of " + SharedResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        this.respondedClients.add((SharedResponse) response);

        super.onResponse(response);
    }

    @Override
    public SharedExchangeHandlerResult getResult() {
        boolean hasAccepted = true;

        for (SharedResponse response : this.respondedClients) {
            if (! response.hasAccepted()) {
                hasAccepted = false;
                logger.info("Client " + response.getClientDevice() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + " has not accepted the SharedOffer " + response.getExchangeId());
            }
        }

        return new SharedExchangeHandlerResult(hasAccepted);
    }
}
