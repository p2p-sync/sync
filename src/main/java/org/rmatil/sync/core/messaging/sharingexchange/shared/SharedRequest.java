package org.rmatil.sync.core.messaging.sharingexchange.shared;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.version.api.AccessType;

import java.util.List;
import java.util.UUID;

public class SharedRequest extends ARequest {

    protected UUID negotiatedFileId;

    protected String sharer;

    protected AccessType accessType;

    protected String relativePath;

    public SharedRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, UUID negotiatedFileId, String sharer, AccessType accessType, String relativePath) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.negotiatedFileId = negotiatedFileId;
        this.sharer = sharer;
        this.accessType = accessType;
        this.relativePath = relativePath;
    }

    public UUID getNegotiatedFileId() {
        return negotiatedFileId;
    }

    public String getSharer() {
        return sharer;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getRelativePath() {
        return relativePath;
    }
}
