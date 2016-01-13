package org.rmatil.sync.core.messaging.fileexchange.offer;

/**
 * Describes the result of a file offering
 */
public class FileOfferExchangeHandlerResult {

    /**
     * Whether all clients have accepted the offer
     */
    protected boolean hasOfferAccepted;

    /**
     * Whether some clients have detected a conflict
     */
    protected boolean hasConflictDetected;

    /**
     * @param hasOfferAccepted    Whether all clients have accepted the offer
     * @param hasConflictDetected Whether some clients have detected a conflict
     */
    public FileOfferExchangeHandlerResult(boolean hasOfferAccepted, boolean hasConflictDetected) {
        this.hasOfferAccepted = hasOfferAccepted;
        this.hasConflictDetected = hasConflictDetected;
    }

    /**
     * Returns true, if all clients have accepted the offer
     *
     * @return True, if all clients have accepted the offer, false otherwise
     */
    public boolean hasOfferAccepted() {
        return hasOfferAccepted;
    }

    /**
     * Returns true, if any client has detected a conflict.
     *
     * @return True, if a conflict has been detected, false otherwise
     */
    public boolean hasConflictDetected() {
        return hasConflictDetected;
    }

    @Override
    public String toString() {
        return "HasAccepted: " + hasOfferAccepted + ", HasConflict: " + hasConflictDetected;
    }
}
