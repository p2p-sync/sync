package org.rmatil.sync.core.syncer.background.syncresult;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * The request containing the final merged and zipped object store
 */
public class SyncResultRequest extends ARequest {

    /**
     * The final merged and zipped object store
     */
    protected byte[] zippedObjectStore;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public SyncResultRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, byte[] zippedObjectStore) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.zippedObjectStore = zippedObjectStore;
    }

    /**
     * Returns the final merged and zipped object store
     *
     * @return The final merged and zipped object store
     */
    public byte[] getZippedObjectStore() {
        return zippedObjectStore;
    }
}
