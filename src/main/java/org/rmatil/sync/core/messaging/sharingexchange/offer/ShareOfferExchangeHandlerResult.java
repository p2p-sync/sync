package org.rmatil.sync.core.messaging.sharingexchange.offer;

import java.util.UUID;

public class ShareOfferExchangeHandlerResult {

    protected boolean hasAccepted;

    protected UUID negotiatedFileId;

    public ShareOfferExchangeHandlerResult(boolean hasAccepted, UUID negotiatedFileId) {
        this.hasAccepted = hasAccepted;
        this.negotiatedFileId = negotiatedFileId;
    }

    public boolean hasAccepted() {
        return hasAccepted;
    }

    public UUID getNegotiatedFileId() {
        return negotiatedFileId;
    }
}
