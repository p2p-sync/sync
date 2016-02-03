package org.rmatil.sync.core.messaging.base;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class AResponse extends AMessage implements IResponse {

    /**
     * The client device which sends this request
     */
    protected ClientDevice clientDevice;

    /**
     * All addresses which should receive this request
     */
    protected ClientLocation receiverAddress;

    /**
     * @param exchangeId The id of the exchange to which this request belongs
     * @param clientDevice The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     */
    public AResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, ClientLocation receiverAddress) {
        super(exchangeId, statusCode);
        this.clientDevice = clientDevice;
        this.receiverAddress = receiverAddress;
    }


    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    @Override
    public ClientLocation getReceiverAddress() {
        return this.receiverAddress;
    }
}
