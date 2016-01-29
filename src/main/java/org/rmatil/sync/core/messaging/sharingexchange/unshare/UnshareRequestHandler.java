package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.IExtendedLocalStateRequestCallback;
import org.rmatil.sync.core.messaging.sharingexchange.unshared.UnsharedExchangeHandler;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class UnshareRequestHandler implements IExtendedLocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(UnshareRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected IClientManager       clientManager;
    protected IEventAggregator     eventAggregator;
    protected UnshareRequest       request;
    protected MBassador<IBusEvent> globalEventBus;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    @Override
    public void setClientManager(IClientManager clientManager) {
        this.clientManager = clientManager;
    }

    @Override
    public void setEventAggregator(IEventAggregator eventAggregator) {
        this.eventAggregator = eventAggregator;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof UnshareRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + UnshareRequest.class.getName());
        }

        this.request = (UnshareRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // add file id from request to the attached file path
            logger.info("Unsharing file for id " + this.request.getFileId());

            PathObject sharedObject = this.objectStore.getObjectManager().getObjectForPath(
                    this.client.getIdentifierManager().getKey(this.request.getFileId())
            );

            logger.info("Found file on path " + sharedObject.getAbsolutePath() + " for file with id " + this.request.getFileId());

            this.objectStore.getSharerManager().removeSharer(
                    this.request.getClientDevice().getUserName(),
                    sharedObject.getAbsolutePath()
            );

            this.sendResponse(true);

            UUID exchangeId = UUID.randomUUID();
            logger.info("Starting to send unshare request for file id " + this.request.getFileId() + " to own clients. Request " + exchangeId);
            UnsharedExchangeHandler unsharedExchangeHandler = new UnsharedExchangeHandler(
                    this.client,
                    this.clientManager,
                    this.objectStore,
                    sharedObject.getAbsolutePath(),
                    this.request.getFileId(),
                    this.request.getClientDevice().getUserName(),
                    exchangeId
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, unsharedExchangeHandler);

            Thread unsharedExchangeHandlerThread = new Thread(unsharedExchangeHandler);
            unsharedExchangeHandlerThread.setName("UnsharedExchangeHandler-" + exchangeId);
            unsharedExchangeHandlerThread.start();

            try {
                unsharedExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting that own clients are unsharing file " + this.request.getFileId() + " for exchange " + exchangeId);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

            if (! unsharedExchangeHandler.isCompleted()) {
                logger.error("UnsharedExchangeHandler should be completed after awaiting. Own clients may be inconsistent for sharing until next sync. Exchange " + exchangeId);
            }


        } catch (Exception e) {
            logger.error("Got exception in UnshareRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the given response back to the client
     *
     * @param hasAccepted Whether the client has accepted the shared request
     */
    public void sendResponse(boolean hasAccepted) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        IResponse response = new UnshareResponse(
                this.request.getExchangeId(),
                new ClientDevice(
                        this.client.getUser().getUserName(),
                        this.client.getClientDeviceId(),
                        this.client.getPeerAddress()
                ),
                new ClientLocation(
                        this.request.getClientDevice().getClientDeviceId(),
                        this.request.getClientDevice().getPeerAddress()
                ),
                hasAccepted
        );

        this.client.sendDirect(response.getReceiverAddress().getPeerAddress(), response);
    }
}
