package org.rmatil.sync.core.messaging;

public enum StatusCode {

    NONE,

    ACCEPTED,

    /**
     * If the sending client is not authorized
     * to start a particular action on the receiving client
     */
    ACCESS_DENIED,

    DENIED,

    FILE_CORRUPT,

    FILE_MISSING
}
