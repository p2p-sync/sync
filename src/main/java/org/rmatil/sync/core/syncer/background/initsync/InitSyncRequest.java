package org.rmatil.sync.core.syncer.background.initsync;

import org.rmatil.sync.core.messaging.fileexchange.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class InitSyncRequest extends ARequest {

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

    public ClientDevice getElectedMaster() {
        return electedMaster;
    }
}
