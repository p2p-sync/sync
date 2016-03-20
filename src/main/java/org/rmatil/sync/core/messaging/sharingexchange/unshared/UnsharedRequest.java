package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

/**
 * A request notifying the own clients about a sharer
 * which lost access rights on a particular element
 */
public class UnsharedRequest extends ARequest {

    private static final long serialVersionUID = - 7252997893901033740L;

    /**
     * The identifier of the affected element
     */
    protected UUID fileId;

    /**
     * The sharer's username which lost access rights
     */
    protected String sharer;

    /**
     * @param exchangeId        The exchange id to which this request belongs
     * @param statusCode        The status code of this request
     * @param clientDevice      The client device which is sending this request
     * @param receiverAddresses The addresses of the receivers of this request
     * @param sharer            The sharer's username which lost access
     * @param fileId            The identifier of the affected file
     */
    public UnsharedRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses, String sharer, UUID fileId) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.sharer = sharer;
        this.fileId = fileId;
    }

    /**
     * The sharer's username which lost access
     *
     * @return The username
     */
    public String getSharer() {
        return sharer;
    }

    /**
     * The identifier of the affected element
     *
     * @return The identifier
     */
    public UUID getFileId() {
        return fileId;
    }
}
