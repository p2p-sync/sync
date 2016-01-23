package org.rmatil.sync.core.syncer.background.syncresult;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.syncer.background.BlockingBackgroundSyncer;
import org.rmatil.sync.core.syncer.background.syncobjectstore.ObjectStoreSyncer;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sends the merged object store from {@link ObjectStoreSyncer} to all other clients
 * and forces them to update their object stores as well.
 *
 * @see BlockingBackgroundSyncer
 */
public class SyncResultExchangeHandler extends ANetworkHandler<SyncResultExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SyncResultExchangeHandler.class);
    /**
     * The id of the file exchange
     */
    protected UUID exchangeId;

    /**
     * A storage adapter to access the synchronized folder
     */
    protected IClientManager clientManager;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    /**
     * The final merged and zipped object store
     */
    protected byte[] zippedObjectStore;

    /**
     * A list of clients which responded to the file offer request
     */
    protected List<IResponse> respondedClients;

    /**
     * The object store to access the file versions
     */
    protected IObjectStore objectStore;

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The global bus event used for adding events
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * @param exchangeId The exchange id to use for the exchange
     * @param clientManager The client manager to fetch all client locations from
     * @param client The client to use for sending messages
     * @param zippedObjectStore The final merged and zipped object store
     */
    public SyncResultExchangeHandler(UUID exchangeId, IClientManager clientManager, IClient client, byte[] zippedObjectStore) {
        super(client);
        this.clientDevice = new ClientDevice(super.client.getUser().getUserName(), super.client.getClientDeviceId(), super.client.getPeerAddress());
        this.exchangeId = exchangeId;
        this.clientManager = clientManager;
        this.respondedClients = new ArrayList<>();
        this.zippedObjectStore = zippedObjectStore;
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

            SyncResultRequest syncResultRequest = new SyncResultRequest(
                    this.exchangeId,
                    this.clientDevice,
                    clientLocations,
                    this.zippedObjectStore
            );

            super.sendRequest(syncResultRequest);

        } catch (Exception e) {
            logger.error("Got error in SyncResultExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        this.respondedClients.add(iResponse);
        super.countDownLatch.countDown();
    }

    @Override
    public SyncResultExchangeHandlerResult getResult() {
        return new SyncResultExchangeHandlerResult();
    }
}
