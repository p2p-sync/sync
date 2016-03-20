package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

/**
 * Send this request object to clients, to offer
 * a file creation / modification / deletion.
 */
public class FileOfferRequest extends ARequest {

    private static final long serialVersionUID = - 3074601001422173408L;

    /**
     * The event to offer to other clients
     */
    protected SerializableEvent event;

    /**
     * The file Id of the file
     */
    protected UUID fileId;

    /**
     * The owner of the file
     */
    protected String owner;

    /**
     * @param exchangeId        The id of the file exchange
     * @param statusCode        The status code for the request
     * @param clientDevice      The client device which sends this request
     * @param event             The event to propagate to other clients
     * @param receiverAddresses All client locations which should receive this request
     */
    public FileOfferRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, UUID fileId, String owner, SerializableEvent event, List<NodeLocation> receiverAddresses) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.fileId = fileId;
        this.owner = owner;
        this.event = event;
    }

    /**
     * Returns the event which should be propagated to all clients
     *
     * @return The event to propagate
     */
    public SerializableEvent getEvent() {
        return event;
    }

    /**
     * Returns the file id. May be null
     *
     * @return The file id
     */
    public UUID getFileId() {
        return fileId;
    }

    /**
     * Returns the owner. May be null.
     *
     * @return The owner of the file
     */
    public String getOwner() {
        return owner;
    }
}
