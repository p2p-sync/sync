package org.rmatil.sync.core.syncer.background.masterelection;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.fileexchange.delete.FileDeleteExchangeHandler;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers to the corresponding {@link MasterElectionRequest}.
 * Currently, only positive responses are sent back. This may
 * change in the future.
 *
 * @see MasterElectionExchangeHandler
 */
public class MasterElectionRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    /**
     * A storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The object store of the synchronized folder
     */
    protected IObjectStore objectStore;

    /**
     * The client to send messages
     */
    protected IClient client;

    /**
     * The global event bus
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The request for which to respond
     */
    protected MasterElectionRequest request;

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
        if (! (iRequest instanceof MasterElectionRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + MasterElectionRequest.class.getName());
        }

        this.request = (MasterElectionRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Received master election request. Accepting...");

            this.client.sendDirect(this.request.getClientDevice().getPeerAddress(), new MasterElectionResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    true
            ));

        } catch (Exception e) {
            logger.error("Error in MasterElectionRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }
}
