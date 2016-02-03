package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class UnsharedResponse extends AResponse {

    public UnsharedResponse(UUID exchangeId, StatusCode statusCode, ClientDevice clientDevice, ClientLocation receiverAddress) {
        super(exchangeId, statusCode, clientDevice, receiverAddress);
    }

}
