package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

/**
 * A response to the corresponding {@link FetchObjectStoreRequest}.
 * Contains the zipped object store.
 */
public class FetchObjectStoreResponse extends AResponse {

    /**
     * The zipped object store
     */
    protected byte[] objectStore;

    /**
     * @param exchangeId      The id of the exchange to which this request belongs
     * @param clientDevice    The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     * @param objectStore     The object store as zip file
     */
    public FetchObjectStoreResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, ClientLocation receiverAddress, byte[] objectStore) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
        this.objectStore = objectStore;
    }

    /**
     * The zipped object store
     *
     * @return The zipped object store
     */
    public byte[] getObjectStore() {
        return objectStore;
    }
}
