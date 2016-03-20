package org.rmatil.sync.core.messaging.fileexchange.delete;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.UUID;

/**
 * The response of a corresponding {@link FileDeleteRequest}.
 *
 * @see FileDeleteExchangeHandler
 */
public class FileDeleteResponse extends AResponse {

    private static final long serialVersionUID = 4369723874688481606L;

    /**
     * @param exchangeId      The id of the exchange to which this request belongs
     * @param statusCode      The status code of this response
     * @param clientDevice    The client device which sends this request
     * @param receiverAddress The client which had sent the corresponding request to this response
     */
    public FileDeleteResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, NodeLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }
}
