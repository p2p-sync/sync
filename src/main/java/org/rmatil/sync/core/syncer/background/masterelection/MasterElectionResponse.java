package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class MasterElectionResponse extends AResponse {

    /**
     * Whether the client accepts the corresponding {@link MasterElectionRequest}
     */
    protected boolean hasAccepted;

    /**
     * @param exchangeId      The id of the exchange to which this request belongs
     * @param clientDevice    The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     * @param hasAccepted     Whether the client which received the corresponding request accepts the request
     */
    public MasterElectionResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean hasAccepted) {
        super(exchangeId, clientDevice, receiverAddress);
        this.hasAccepted = hasAccepted;
    }

    /**
     * Whether the client accepts the corresponding {@link MasterElectionRequest}
     *
     * @return True, if accepted, false otherwise
     */
    public boolean isHasAccepted() {
        return hasAccepted;
    }
}
