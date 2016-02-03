package org.rmatil.sync.core.messaging.fileexchange.delete;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * The request for a file delete exchange
 *
 * @see FileDeleteExchangeHandler
 */
public class FileDeleteRequest extends ARequest {

    /**
     * The path to delete relative to the synced folder's root
     */
    protected String pathToDelete;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param statusCode        The status code of this request
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this request
     * @param pathToDelete      The path which should be deleted
     */
    public FileDeleteRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, String pathToDelete) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.pathToDelete = pathToDelete;
    }

    /**
     * Returns the path to delete (relative to the synced folder)
     *
     * @return The path to delete
     */
    public String getPathToDelete() {
        return pathToDelete;
    }
}
