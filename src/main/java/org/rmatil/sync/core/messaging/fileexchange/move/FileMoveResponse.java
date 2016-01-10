package org.rmatil.sync.core.messaging.fileexchange.move;

import org.rmatil.sync.core.messaging.fileexchange.base.AResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.UUID;

public class FileMoveResponse extends AResponse {

    protected boolean hasAccepted;

    public FileMoveResponse(UUID exchangeId, ClientDevice clientDevice, ClientLocation clientLocation, boolean hasAccepted) {
        super(exchangeId, clientDevice, clientLocation);
        this.hasAccepted = hasAccepted;
    }

    public boolean isHasAccepted() {
        return hasAccepted;
    }
}
