package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsharedRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(UnsharedRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected UnsharedRequest      request;
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
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof UnsharedRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + UnsharedRequest.class.getName());
        }

        this.request = (UnsharedRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // add file id from request to the attached file path
            logger.info("Starting to unshare file for id " + this.request.getFileId() + " with sharer " + this.request.getSharer());

            PathObject sharedObject = this.objectStore.getObjectManager().getObjectForPath(
                    this.client.getIdentifierManager().getKey(this.request.getFileId())
            );

            logger.trace("Found file on path " + sharedObject.getAbsolutePath() + " for file with id " + this.request.getFileId());

            this.objectStore.getSharerManager().removeSharer(
                    this.request.getSharer(),
                    sharedObject.getAbsolutePath()
            );

            this.sendResponse(true);

        } catch (Exception e) {
            logger.error("Got exception in UnsharedRequestHandler. Message: " + e.getMessage(), e);
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

        IResponse response = new UnsharedResponse(
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
