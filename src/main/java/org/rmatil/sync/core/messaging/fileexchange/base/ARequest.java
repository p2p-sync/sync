package org.rmatil.sync.core.messaging.fileexchange.base;

import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public abstract class ARequest implements IRequest {

    /**
     * The id of the exchange to which this request belongs
     */
    protected UUID exchangeId;

    /**
     * The client device which sends this request
     */
    protected ClientDevice clientDevice;

    /**
     * All addresses which should receive this request
     */
    protected List<ClientLocation> receiverAddresses;

    /**
     * @param exchangeId The id of the exchange to which this request belongs
     * @param clientDevice The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public ARequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses) {
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.receiverAddresses = receiverAddresses;
    }

    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return this.receiverAddresses;
    }

    @Override
    public UUID getExchangeId() {
        return this.exchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
