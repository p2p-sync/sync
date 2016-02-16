package org.rmatil.sync.core.messaging.base;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

public abstract class ARequest extends AMessage implements IRequest {

    /**
     * /**
     * The client device which sends this request
     */
    protected ClientDevice clientDevice;

    /**
     * All addresses which should receive this request
     */
    protected List<NodeLocation> receiverAddresses;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param statusCode        The statusCode of the message
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this requeust
     */
    public ARequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses) {
        super(exchangeId, statusCode);
        this.clientDevice = clientDevice;
        this.receiverAddresses = receiverAddresses;
    }

    @Override
    public List<NodeLocation> getReceiverAddresses() {
        return this.receiverAddresses;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
