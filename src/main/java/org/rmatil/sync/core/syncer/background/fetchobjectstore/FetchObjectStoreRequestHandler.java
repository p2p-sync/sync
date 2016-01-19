package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.Zip;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionRequest;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchObjectStoreRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FetchObjectStoreRequestHandler.class);

    protected IStorageAdapter         storageAdapter;
    protected IObjectStore            objectStore;
    protected IClient                 client;
    protected FetchObjectStoreRequest request;
    protected MBassador<IBusEvent>    globalEventBus;

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
        if (! (iRequest instanceof FetchObjectStoreRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + MasterElectionRequest.class.getName());
        }

        this.request = (FetchObjectStoreRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // zip object store
            byte[] zipFile = Zip.zipObjectStore(this.objectStore);

            // send zip
            FetchObjectStoreResponse syncObjectStoreResponse = new FetchObjectStoreResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    zipFile
            );

            this.client.sendDirect(this.request.getClientDevice().getPeerAddress(),
                    syncObjectStoreResponse
            );

        } catch (Exception e) {
            logger.error("Got exception in FetchObjectStoreRequestHandler. Message: " + e.getMessage(), e);
        }
    }
}
