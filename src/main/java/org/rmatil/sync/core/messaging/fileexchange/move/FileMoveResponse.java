package org.rmatil.sync.core.messaging.fileexchange.move;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

/**
 * Send this response as answer whenever a {@link FileMoveRequest}
 * has been received
 */
public class FileMoveResponse extends AResponse {

    /**
     * Whether the request has been accepted
     */
    protected boolean hasAccepted;

    /**
     * @param exchangeId      The exchange id
     * @param clientDevice    The client device sending this request
     * @param receiverAddress The receiver of this response
     * @param hasAccepted     Whether the request has been accepted or not
     */
    public FileMoveResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean hasAccepted) {
        super(exchangeId, clientDevice, receiverAddress);
        this.hasAccepted = hasAccepted;
    }

    /**
     * Returns whether the corresponding {@link FileMoveRequest} has been accepted or not
     *
     * @return True, if accepted, false otherwise
     */
    public boolean hasAccepted() {
        return hasAccepted;
    }
}
