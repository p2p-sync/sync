package org.rmatil.sync.core.messaging.sharingexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShareOfferRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(ShareOfferRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected ShareOfferRequest    request;
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
        if (! (iRequest instanceof ShareOfferRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + ShareOfferRequest.class.getName());
        }

        this.request = (ShareOfferRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(this.request.getPath());

            if (null == pathObject.getFileId() || this.request.getFileId().equals(pathObject.getFileId())) {
                // we accept the offer response
                this.sendResponse(true);
                return;
            }

            // we have a different file id -> so we do not accept the offer
            this.sendResponse(false);

        } catch (Exception e) {
            logger.error("Got exception in ShareOfferRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    protected void sendResponse(boolean accepted) {
        this.client.sendDirect(
                this.request.getClientDevice().getPeerAddress(),
                new ShareOfferResponse(
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
                        accepted
                )
        );
    }
}
