package org.rmatil.sync.core.syncer.background.synccomplete;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Sends a notification to all clients causing them to
 * restart their event aggregators and propagate any changes
 * made in the mean time to all clients.
 */
public class SyncCompleteExchangeHandler extends ANetworkHandler<SyncCompleteExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SyncCompleteExchangeHandler.class);

    /**
     * The client manager to fetch all client locations
     */
    protected IClientManager clientManager;

    /**
     * The id for this exchange
     */
    protected UUID exchangeId;

    /**
     * @param client The client to send messages
     * @param  clientManager The client manager to fetch all client locations from
     * @param exchangeId The id for this exchange
     */
    public SyncCompleteExchangeHandler(IClient client, IClientManager clientManager, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {

        } catch (Exception e) {
            logger.error("Got exception in SyncCompleteExchangeHandler. Message: " + e.getMessage());
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {

    }

    @Override
    public SyncCompleteExchangeHandlerResult getResult() {
        return null;
    }
}
