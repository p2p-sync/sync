package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

/**
 * Send this response object to clients which have previous
 * send a file offer request.
 */
public class FileOfferResponse extends AResponse {

    /**
     * Whether the sending client has accepted the offer
     */
    protected boolean acceptedOffer;

    /**
     * Whether the sending client has detected a conflict
     */
    protected boolean conflict;

    /**
     * Whether a further request to the client will not
     * change anything on the filesystem, e.g.
     * if the delete event will not delete the file because
     * it is already deleted on the client
     */
    protected boolean requestObsolete;

    /**
     * @param exchangeId      The id of the file exchange
     * @param clientDevice    The client device which is sending this response
     * @param receiverAddress The address of the client to which this response should be sent
     * @param acceptedOffer   Whether the client has accepted the previous file offer
     * @param conflict        Whether the client has detected a conflict
     * @param requestObsolete Whether the following up request would be obsolete (i.e. would not change anything on the state of the client's filesystem)
     */
    public FileOfferResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean acceptedOffer, boolean conflict, boolean requestObsolete) {
        super(exchangeId, clientDevice, receiverAddress);
        this.acceptedOffer = acceptedOffer;
        this.conflict = conflict;
        this.requestObsolete = requestObsolete;
    }

    /**
     * Whether the client has accepted the file offering
     *
     * @return True, if accepted, false otherwise
     */
    public boolean hasAcceptedOffer() {
        return acceptedOffer;
    }

    /**
     * Whether the client has detected a local conflict
     *
     * @return True, if a conflict has been detected, false otherwise
     */
    public boolean hasConflict() {
        return conflict;
    }

    /**
     * Returns whether the following up
     * request would be obsolete (i.e. would not change anything
     * on the state of the client's filesystem).
     * <p>
     * <i>Example</i>: If the following up delete request for which the corresponding
     * {@link FileOfferRequest} was sent would not delete the file because
     * it already does not exist on the client.
     *
     * @return True, if the follwing up request is obsolete. False otherwise
     */
    public boolean isRequestObsolete() {
        return requestObsolete;
    }
}
