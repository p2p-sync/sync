package org.rmatil.sync.core.messaging.fileexchange.offer;

public class FileOfferExchangeHandlerResult {

    protected boolean hasOfferAccepted;

    protected boolean hasConflictDetected;

    public FileOfferExchangeHandlerResult(boolean hasOfferAccepted, boolean hasConflictDetected) {
        this.hasOfferAccepted = hasOfferAccepted;
        this.hasConflictDetected = hasConflictDetected;
    }

    public boolean isHasOfferAccepted() {
        return hasOfferAccepted;
    }

    public boolean isHasConflictDetected() {
        return hasConflictDetected;
    }

    public String toString() {
        return "HasAccepted: " + hasOfferAccepted + ", HasConflict: " + hasConflictDetected;
    }
}
