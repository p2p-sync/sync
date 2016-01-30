package org.rmatil.sync.core.messaging.fileexchange.delete;

import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

/**
 * The response of a corresponding {@link FileDeleteRequest}.
 *
 * @see FileDeleteExchangeHandler
 */
public class FileDeleteResponse extends AResponse {

    /**
     * Whether the client sending this response has accepted the delete
     */
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

    /**
     * Returns whether the client sending this response
     * has accepted the delete request.
     *
     * @return True, if accepted, false otherwise
     */
    public boolean hasAccepted() {
        return hasAccepted;
    }
}
