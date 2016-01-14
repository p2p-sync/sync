package org.rmatil.sync.core.messaging.fileexchange.delete;

import org.rmatil.sync.core.messaging.fileexchange.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class FileDeleteRequest extends ARequest {

    protected String  pathToDelete;

    /**
     * @param exchangeId        The id of the exchange to which this request belongs
     * @param clientDevice      The client device which sends this request
     * @param receiverAddresses All client locations which should receive this request
     * @param pathToDelete      The path which should be deleted
     */
    public FileDeleteRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, String pathToDelete) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.pathToDelete = pathToDelete;
    }

    public String getPathToDelete() {
        return pathToDelete;
    }
}
