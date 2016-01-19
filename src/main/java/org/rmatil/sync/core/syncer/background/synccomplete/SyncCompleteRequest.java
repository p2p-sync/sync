package org.rmatil.sync.core.syncer.background.synccomplete;

import org.rmatil.sync.core.messaging.fileexchange.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class SyncCompleteRequest extends ARequest {

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public SyncCompleteRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses) {
        super(exchangeId, clientDevice, receiverAddresses);
    }
}
