package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

/**
 * A request notifying the sharer that he lost
 * access rights on a particular element
 */
public class UnshareRequest extends ARequest {

    private static final long serialVersionUID = 9032357368596899913L;

    /**
     * The id of the affected element
     */
    protected UUID fileId;

    /**
     * @param exchangeId        The exchange id to which this request belongs
     * @param statusCode        The status code of this request
     * @param clientDevice      The client device which is sending this request
     * @param receiverAddresses The addresses of the receivers of this request
     * @param fileId            The id of the affected file
     */
    public UnshareRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses, UUID fileId) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.fileId = fileId;
    }

    /**
     * Get the identifier of the affected element
     *
     * @return The identifier
     */
    public UUID getFileId() {
        return fileId;
    }
}
