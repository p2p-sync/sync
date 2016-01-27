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

    protected UUID negotiatedFileId;

    protected String sharer;

    protected AccessType accessType;

    protected String relativeFilePath;

    protected boolean isFile;

    protected UUID exchangeId;

    protected List<SharedResponse> respondedClients;

    public SharedExchangeHandler(IClient client, IClientManager clientManager, IObjectStore objectStore, UUID negotiatedFileId, String sharer, AccessType accessType, String relativeFilePath, boolean isFile, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.objectStore = objectStore;
        this.negotiatedFileId = negotiatedFileId;
        this.sharer = sharer;
        this.accessType = accessType;
        this.relativeFilePath = relativeFilePath;
        this.isFile = isFile;
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
                    this.negotiatedFileId,
                    this.sharer,
                    this.accessType,
                    this.relativeFilePath
            );

            super.sendRequest(request);

            // now we also write the changes to our object store
            PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(this.relativeFilePath);
            pathObject.setFileId(this.negotiatedFileId);

            // write file id
            this.objectStore.getObjectManager().writeObject(pathObject);

            // add sharer to the file
            this.objectStore.getSharerManager().addSharer(
                    this.sharer,
                    this.accessType,
                    this.relativeFilePath
            );

        } catch (Exception e) {
            logger.error("Got exception in SharedExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        logger.info("Received response for exchange " + response.getExchangeId() + " of client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")");

        if (! (response instanceof SharedResponse)) {
            logger.error("Expected response to be instance of " + SharedResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        this.respondedClients.add((SharedResponse) response);

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
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
