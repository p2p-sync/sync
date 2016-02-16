package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;

import java.util.List;
import java.util.UUID;

public class UnshareRequest extends ARequest {

    protected UUID fileId;

    public UnshareRequest(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, List<NodeLocation> receiverAddresses, UUID fileId) {
        super(exchangeId, statusCode, clientDevice, receiverAddresses);
        this.fileId = fileId;
    }

    public UUID getFileId() {
        return fileId;
    }
}
