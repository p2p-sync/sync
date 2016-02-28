package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class UnsharedExchangeHandler extends ANetworkHandler<UnsharedExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(UnsharedExchangeHandler.class);

    protected INodeManager nodeManager;

    protected IObjectStore objectStore;

    protected String relativeFilePath;

    protected UUID fileId;

    protected String sharer;

    protected UUID exchangeId;

    public UnsharedExchangeHandler(INode client, INodeManager nodeManager, IObjectStore objectStore, String relativeFilePath, UUID fileId, String sharer, UUID exchangeId) {
        super(client);
        this.nodeManager = nodeManager;
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
            List<NodeLocation> clientLocations;
            try {
                clientLocations = this.nodeManager.getNodeLocations(super.node.getUser().getUserName());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.node.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            // TODO: why not sending the file path instead the file id?

            UnsharedRequest unsharedRequest = new UnsharedRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    new ClientDevice(
                            super.node.getUser().getUserName(),
                            super.node.getClientDeviceId(),
                            super.node.getPeerAddress()
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
