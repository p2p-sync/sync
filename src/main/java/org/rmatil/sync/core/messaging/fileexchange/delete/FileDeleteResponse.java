package org.rmatil.sync.core.messaging.fileexchange.delete;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class FileDeleteResponse extends AResponse {

    protected boolean hasAccepted;

    /**
     * @param exchangeId      The id of the exchange to which this request belongs
     * @param clientDevice    The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     * @param hasAccepted     Whether the client sending this response has accepted the corresponding request
     */
    public FileDeleteResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean hasAccepted) {
        super(exchangeId, clientDevice, receiverAddress);
        this.hasAccepted = hasAccepted;
    }

    public boolean isHasAccepted() {
        return hasAccepted;
    }
}
