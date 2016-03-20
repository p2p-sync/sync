package org.rmatil.sync.core.messaging.sharingexchange.shared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.UUID;

/**
 * A response used as answer of the corresponding {@link SharedRequest}
 */
public class SharedResponse extends AResponse {

    private static final long serialVersionUID = - 8812400775660115622L;

    /**
     * @param exchangeId      The exchange id to which this response belongs
     * @param statusCode      The status code of this response
     * @param clientDevice    The client device which is sending this response
     * @param receiverAddress The receiver of this response
     */
    public SharedResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }

}
