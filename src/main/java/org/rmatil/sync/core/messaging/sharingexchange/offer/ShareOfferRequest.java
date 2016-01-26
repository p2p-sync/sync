package org.rmatil.sync.core.messaging.sharingexchange.offer;

import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

public class ShareOfferRequest extends ARequest {

    protected UUID fileId;

    protected String path;

    /**
     * @param exchangeId        The exchange id
     * @param clientDevice      The client device which is sending this request
     * @param receiverAddresses The receiver addresses of this request
     * @param fileId            The file id which is proposed to us
     * @param path              The path to the file which should be shared
     */
    public ShareOfferRequest(UUID exchangeId, ClientDevice clientDevice, List<ClientLocation> receiverAddresses, UUID fileId, String path) {
        super(exchangeId, clientDevice, receiverAddresses);
        this.fileId = fileId;
        this.path = path;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getPath() {
        return path;
    }

}
