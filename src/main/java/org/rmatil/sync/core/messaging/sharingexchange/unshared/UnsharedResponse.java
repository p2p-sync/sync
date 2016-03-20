package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.UUID;

/**
 * The response used as answer for a corresponding {@link UnsharedRequest}
 */
public class UnsharedResponse extends AResponse {

    private static final long serialVersionUID = - 602030547690630951L;

    /**
     * @param exchangeId      The exchange id to which this response belongs
     * @param statusCode      The status code of this response
     * @param clientDevice    The client device which is sending this response
     * @param receiverAddress The receiver of this response
     */
    public UnsharedResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }

}
