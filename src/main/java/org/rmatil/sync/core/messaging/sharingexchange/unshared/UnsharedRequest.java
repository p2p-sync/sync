package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

public class UnsharedRequest extends ARequest {

    protected UUID fileId;

    protected String sharer;

    public UnsharedRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses, String sharer, UUID fileId) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.sharer = sharer;
        this.fileId = fileId;
    }

    public String getSharer() {
        return sharer;
    }

    public UUID getFileId() {
        return fileId;
    }
}
