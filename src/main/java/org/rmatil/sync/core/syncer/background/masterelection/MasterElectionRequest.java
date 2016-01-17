package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.core.messaging.fileexchange.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class MasterElectionRequest extends ARequest {

    protected long timestamp;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     * @param timestamp         The timestamp when the election process has started
     */
    public MasterElectionRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, long timestamp) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
