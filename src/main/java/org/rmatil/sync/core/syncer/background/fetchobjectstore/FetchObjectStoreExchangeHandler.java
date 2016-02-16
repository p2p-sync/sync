package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fetches all object stores as zip files from all other
 * online clients.
 */
public class FetchObjectStoreExchangeHandler extends ANetworkHandler<FetchObjectStoreExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FetchObjectStoreExchangeHandler.class);

    /**
     * The client manager to get all client locations from
     */
    protected INodeManager nodeManager;

    /**
     * The exchange id used for the fetch
     */
    protected UUID exchangeId;

    /**
     * A list of fetched object stores
     */
    protected List<FetchObjectStoreResponse> responses;

    /**
     * @param client        The client to use for sending messages
     * @param nodeManager The client manager to get all other client locations
     * @param exchangeId    The exchange id used for this exchange
     */
    public FetchObjectStoreExchangeHandler(INode client, INodeManager nodeManager, UUID exchangeId) {
        super(client);
        this.nodeManager = nodeManager;
        this.exchangeId = exchangeId;
        this.responses = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            List<NodeLocation> clientLocations;
            try {
                clientLocations = this.nodeManager.getNodeLocations(super.node.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.node.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            FetchObjectStoreRequest syncObjectStoreRequest = new FetchObjectStoreRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    new ClientDevice(super.node.getUser().getUserName(), super.node.getClientDeviceId(), super.node.getPeerAddress()),
                    clientLocations
            );

            super.sendRequest(syncObjectStoreRequest);

        } catch (Exception e) {
            logger.error("Got exception in ObjectStoreSyncer. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof FetchObjectStoreResponse)) {
            logger.error("Expected response to be instance of " + FetchObjectStoreResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        this.responses.add((FetchObjectStoreResponse) response);
        super.onResponse(response);
    }

    @Override
    public FetchObjectStoreExchangeHandlerResult getResult() {
        return new FetchObjectStoreExchangeHandlerResult(this.responses);
    }
}
