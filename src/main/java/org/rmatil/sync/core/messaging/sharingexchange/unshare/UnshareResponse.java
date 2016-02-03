package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class UnshareResponse extends AResponse {

    public UnshareResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, ClientLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }

}
