package org.rmatil.sync.core.messaging.fileexchange.offer;

/**
 * All types a file offer can have.
 *
 * @see FileOfferRequest
 */
public enum FileOfferType {

    /**
     * The file offer is indicating a create offer
     */
    CREATE,

    /**
     * The file offer is indicating a delete offer
     */
    DELETE,

    /**
     * The file offer is indicating a modify content offer
     */
    MODIFY,

    /**
     * The file offer is indicating a move offer
     */
    MOVE
}
