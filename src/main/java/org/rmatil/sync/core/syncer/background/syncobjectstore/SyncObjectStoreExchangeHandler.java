package org.rmatil.sync.core.syncer.background.syncobjectstore;

import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandlerResult;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SyncObjectStoreExchangeHandler extends ANetworkHandler<SyncObjectStoreExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SyncObjectStoreExchangeHandler.class);

    protected IClientManager clientManager;

    protected UUID exchangeId;

    public SyncObjectStoreExchangeHandler(IClient client, IClientManager clientManager, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            FetchObjectStoreExchangeHandler fetchObjectStoreExchangeHandler = new FetchObjectStoreExchangeHandler(
                    super.client,
                    this.clientManager,
                    this.exchangeId
            );

            Thread fetchObjectStoreExchangeHandlerThread = new Thread(fetchObjectStoreExchangeHandler);
            fetchObjectStoreExchangeHandlerThread.setName("FetchObjectStoreExchangeHandler-" +this.exchangeId);
            fetchObjectStoreExchangeHandlerThread.start();

            try {
                fetchObjectStoreExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for FetchObjectStoreExchangeHandler. Message: " + e.getMessage());
            }

            if (!fetchObjectStoreExchangeHandler.isCompleted()) {
                logger.error("FetchObjectStoreExchangeHandler should be completed after waiting. Aborting syncing of object store");
                return;
            }

            FetchObjectStoreExchangeHandlerResult result = fetchObjectStoreExchangeHandler.getResult();

            // TODO: merge these fcking object stores somehow



        } catch (Exception e) {
            logger.error("Got exception in SyncObjectStoreExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public SyncObjectStoreExchangeHandlerResult getResult() {
        return null;
    }
}
