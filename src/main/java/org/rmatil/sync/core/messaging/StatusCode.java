package org.rmatil.sync.core.messaging;

import org.rmatil.sync.core.messaging.base.AMessage;
import org.rmatil.sync.core.messaging.base.ARequest;
import org.rmatil.sync.core.messaging.base.AResponse;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushExchangeHandler;

/**
 * Similar to HTTP responses, a {@link AMessage}, the base class
 * for an {@link ARequest} resp. {@link AResponse}, contains
 * a flag indicating the message's status.
 * <p>
 * However, not only responses may have a status but also
 * requests. The {@link FilePushExchangeHandler} uses this flag
 * to indicate whether a file has changed in the mean time of synchronising
 * to allow the receiving client to restart the exchange.
 */
public enum StatusCode {

    /**
     * Used for initial requests
     * which do not have any status yet
     */
    NONE,

    /**
     * If the request was accepted
     * and the corresponding operation executed
     */
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
     * file has changed while transmitting
     */
    FILE_CHANGED,

    /**
     * The status code of a file exchange, if the
     * requested file has been lost
     */
    FILE_MISSING
}
