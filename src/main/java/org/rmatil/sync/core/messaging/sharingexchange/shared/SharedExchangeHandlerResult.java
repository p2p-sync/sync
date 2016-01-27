package org.rmatil.sync.core.messaging.sharingexchange.shared;

public class SharedExchangeHandlerResult {

    protected boolean hasAccepted;

    public SharedExchangeHandlerResult(boolean hasAccepted) {
        this.hasAccepted = hasAccepted;
    }

    public boolean isHasAccepted() {
        return hasAccepted;
    }
}
