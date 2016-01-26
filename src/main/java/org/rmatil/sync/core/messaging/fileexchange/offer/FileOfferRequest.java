package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * Send this request object to clients, to offer
 * a file creation / modification / deletion.
 */
public class FileOfferRequest extends ARequest {

    /**
     * The event to offer to other clients
     */
    protected SerializableEvent event;

    /**
     * @param exchangeId        The id of the file exchange
     * @param clientDevice      The client device which sends this request
     * @param event             The event to propagate to other clients
     * @param receiverAddresses All client locations which should receive this request
     */
    public FileOfferRequest(UUID exchangeId, ClientDevice clientDevice, SerializableEvent event, List<ClientLocation> receiverAddresses) {
        super(exchangeId, clientDevice, receiverAddresses);
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
}
