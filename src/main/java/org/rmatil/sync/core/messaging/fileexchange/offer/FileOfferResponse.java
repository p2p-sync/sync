package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.core.messaging.IResponse;
import org.rmatil.sync.core.model.ClientDevice;

import java.util.UUID;

/**
 * Send this response object to clients which have previous
 * send a file offer request.
 *
 * @see FileOfferResponseHandler
 */
public class FileOfferResponse implements IResponse {

    /**
     * The client device which is sending this response
     */
    protected ClientDevice clientDevice;

    /**
     * Whether the sending client has accepted the offer
     */
    protected boolean acceptedOffer;

    /**
     * Whether the sending client has detected a conflict
     */
    protected boolean conflict;

    /**
     * The id of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * @param fileExchangeId The id of the file exchange
     * @param clientDevice   The client device which is sending this response
     * @param acceptedOffer  Whether the client has accepted the previous file offer
     * @param conflict       Whether the client has detected a conflict
     */
    public FileOfferResponse(UUID fileExchangeId, ClientDevice clientDevice, boolean acceptedOffer, boolean conflict) {
        this.fileExchangeId = fileExchangeId;
        this.clientDevice = clientDevice;
        this.acceptedOffer = acceptedOffer;
        this.conflict = conflict;
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

    @Override
    public UUID getExchangeId() {
        return fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
