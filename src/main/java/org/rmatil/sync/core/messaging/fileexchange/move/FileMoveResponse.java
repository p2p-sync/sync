package org.rmatil.sync.core.messaging.fileexchange.move;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.UUID;

/**
 * Send this response as answer whenever a {@link FileMoveRequest}
 * has been received
 */
public class FileMoveResponse extends AResponse {

    private static final long serialVersionUID = 5787404867724931916L;

    /**
     * @param exchangeId      The exchange id
     * @param clientDevice    The client device sending this request
     * @param receiverAddress The receiver of this response
     */
    public FileMoveResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }
}
