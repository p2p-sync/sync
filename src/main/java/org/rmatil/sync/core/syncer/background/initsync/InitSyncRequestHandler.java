package org.rmatil.sync.core.syncer.background.initsync;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.IExtendedLocalStateRequestCallback;
import org.rmatil.sync.core.syncer.background.syncobjectstore.SyncObjectStoreExchangeHandler;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitSyncRequestHandler implements IExtendedLocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(InitSyncRequestHandler.class);

    /**
     * The storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The object store to access versions
     */
    protected IObjectStore objectStore;

    /**
     * The client to send back messages
     */
    protected IClient client;

    protected IClientManager clientManager;

    protected IEventAggregator eventAggregator;

    /**
     * The file move request from the sender
     */
    protected InitSyncRequest request;

    /**
     * The global event bus to add ignore events
     */
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
        if (! (iRequest instanceof InitSyncRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + InitSyncRequest.class.getName());
        }

        this.request = (InitSyncRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            this.eventAggregator.stop();

            if (this.request.getElectedMaster().getPeerAddress().equals(this.client.getPeerAddress())) {
                logger.info("Got notified that i am the master (" + this.client.getPeerAddress().inetAddress().getHostName() + ":" + this.client.getPeerAddress().tcpPort() + "). Starting ObjectStore Sync in a new thread");

                SyncObjectStoreExchangeHandler syncObjectStoreExchangeHandler = new SyncObjectStoreExchangeHandler(
                        this.client,
                        this.clientManager,
                        this.request.getExchangeId()
                );

                Thread syncObjectStoreExchangeHandlerThread = new Thread(syncObjectStoreExchangeHandler);
                syncObjectStoreExchangeHandlerThread.setName("SyncObjectStoreExchangeHandler-" + this.request.getExchangeId());
                syncObjectStoreExchangeHandlerThread.start();
            }

            // now send back the corresponding response
            this.client.sendDirect(
                    this.request.getClientDevice().getPeerAddress(),
                    new InitSyncResponse(
                            this.request.getExchangeId(),
                            new ClientDevice(
                                    this.client.getUser().getUserName(),
                                    this.client.getClientDeviceId(),
                                    this.client.getPeerAddress()
                            ),
                            new ClientLocation(
                                    this.request.getClientDevice().getClientDeviceId(),
                                    this.request.getClientDevice().getPeerAddress()
                            )
                    )
            );

        } catch (Exception e) {
            logger.error("Got exception in InitSyncRequestHandler. Message: " + e.getMessage(), e);
        }
    }
}
