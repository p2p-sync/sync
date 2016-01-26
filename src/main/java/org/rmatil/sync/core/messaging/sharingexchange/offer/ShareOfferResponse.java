package org.rmatil.sync.core.messaging.sharingexchange.offer;

import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class ShareOfferResponse extends AResponse {

    protected boolean hasAccepted;

    /**
     * @param exchangeId The exchange id
     * @param clientDevice The client device which is sending this response.
     * @param receiverAddress The receiver address of this response
     * @param hasAccepted Whether the client sending this response has accepted the corresponding request or not
     */
    public ShareOfferResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean hasAccepted) {
        super(exchangeId, clientDevice, receiverAddress);
        this.hasAccepted = hasAccepted;
    }

    public boolean hasAccepted() {
        return hasAccepted;
    }
}
