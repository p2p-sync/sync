package org.rmatil.sync.core.syncer.background.synccomplete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.IExtendedLocalStateRequestCallback;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incoming {@link SyncCompleteRequest}s.
 * Starts the event aggregator again and calculates any differences
 * made in the mean time to the synchronized folder.
 */
public class SyncCompleteRequestHandler implements IExtendedLocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(SyncCompleteRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected IClientManager       clientManager;
    protected IEventAggregator     eventAggregator;
    protected SyncCompleteRequest  request;
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
        if (! (iRequest instanceof SyncCompleteRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + SyncCompleteRequest.class.getName());
        }

        this.request = (SyncCompleteRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Handling SyncCompleteRequest for exchangeId " + this.request.getExchangeId() + ", i.e. sending response back once we finished syncing the object store");

            // start event aggregator
            this.eventAggregator.start();

            // TODO: resync object store and propagate changes to all clients
            // -> "merge" and populate changes and paths to other clients


        } catch (Exception e) {
            logger.error("Got exception in SyncCompleteRequestHandler. Message: " + e.getMessage(), e);
        }
    }
}
