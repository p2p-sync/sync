package org.rmatil.sync.core.messaging.sharingexchange.shared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.version.api.AccessType;

import java.util.List;
import java.util.UUID;

/**
 * A request used to notify the own clients about a new sharer
 */
public class SharedRequest extends ARequest {

    private static final long serialVersionUID = - 4130107042442671139L;

    /**
     * The new sharer's username
     */
    protected String sharer;

    /**
     * The access type the sharer got
     */
    protected AccessType accessType;

    /**
     * The path to the element on which the sharer
     * gets the new access type
     */
    protected String relativePath;

    /**
     * @param exchangeId        The exchange id to which this request belongs
     * @param statusCode        The status code of this request
     * @param clientDevice      The client device which is sending this request
     * @param receiverAddresses The addresses of the receivers of this request
     * @param sharer            The sharer's username
     * @param accessType        The access type the sharer got
     * @param relativePath      The path to the affected element
     */
    public SharedRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses, String sharer, AccessType accessType, String relativePath) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.sharer = sharer;
        this.accessType = accessType;
        this.relativePath = relativePath;
    }

    /**
     * Get the sharer's username
     *
     * @return The username of the sharer
     */
    public String getSharer() {
        return sharer;
    }

    /**
     * Get the access type the sharer got
     *
     * @return The access type
     */
    public AccessType getAccessType() {
        return accessType;
    }

    /**
     * Get the relative path to the affected element
     *
     * @return The relative path
     */
    public String getRelativePath() {
        return relativePath;
    }
}
