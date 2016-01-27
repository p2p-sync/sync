package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class UnshareRequest extends ARequest {

    protected UUID fileId;

    public UnshareRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, UUID fileId) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.fileId = fileId;
    }

    public UUID getFileId() {
        return fileId;
    }
}
