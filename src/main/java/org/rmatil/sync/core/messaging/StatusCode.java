package org.rmatil.sync.core.messaging;

import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;

public enum StatusCode {

    NONE,

    ACCEPTED,

    /**
     * If the client sending the response
     * has denied the request
     */
    DENIED,

    /**
     * If the client has a conflict if
     * he would execute the requested action
     */
    CONFLICT,

    /**
     * If the client already has the state,
     * offered in an {@link FileOfferRequest}
     */
    REQUEST_OBSOLETE,

    /**
     * If the sending client is not authorized
     * to start a particular action on the receiving client
     */
    ACCESS_DENIED,

    /**
     * The status code of a file exchange message, if the
     * file has changed while transferring
     */
    FILE_CHANGED,

    FILE_CORRUPT,

    /**
     * The status code of a file exchange, if the
     * requested file has been lost
     */
    FILE_MISSING
}
