package org.rmatil.sync.core.syncer.background.synccomplete;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

/**
 * The response to the corresponding {@link SyncCompleteRequest}
 */
public class SyncCompleteResponse extends AResponse {

    /**
     * @param exchangeId      The id of the exchange to which this request belongs
     * @param clientDevice    The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     */
    public SyncCompleteResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress) {
        super(exchangeId, clientDevice, receiverAddress);
    }
}
