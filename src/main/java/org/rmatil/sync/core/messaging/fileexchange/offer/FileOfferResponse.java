package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.UUID;

/**
 * Send this response object to clients which have previous
 * send a file offer request.
 */
public class FileOfferResponse extends AResponse {

    private static final long serialVersionUID = 6931319694062356099L;

    /**
     * @param exchangeId      The id of the file exchange
     * @param statusCode      The status code of the response
     * @param clientDevice    The client device which is sending this response
     * @param receiverAddress The address of the client to which this response should be sent
     */
    public FileOfferResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }

}
