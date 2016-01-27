package org.rmatil.sync.core.messaging.sharingexchange.shared;

import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class SharedResponse extends AResponse {

    protected boolean hasAccepted;

    public SharedResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, boolean hasAccepted) {
        super(exchangeId, clientDevice, receiverAddress);
        this.hasAccepted = hasAccepted;
    }

    public boolean hasAccepted() {
        return hasAccepted;
    }
}
