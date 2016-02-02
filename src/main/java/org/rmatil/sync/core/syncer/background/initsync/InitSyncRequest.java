package org.rmatil.sync.core.syncer.background.initsync;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * Initializes a sync of the object stores.
 *
 * @see InitSyncExchangeHandler
 *
 * @deprecated As of 0.1. Will be removed in future releases.
 */
public class InitSyncRequest extends ARequest {

    /**
     * The elected master which will start the object store sync
     */
    protected ClientDevice electedMaster;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public InitSyncRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, ClientDevice electedMaster) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.electedMaster = electedMaster;
    }

    /**
     * Returns the elected master which will start to sync the object stores
     *
     * @return The elected master
     */
    public ClientDevice getElectedMaster() {
        return electedMaster;
    }
}
