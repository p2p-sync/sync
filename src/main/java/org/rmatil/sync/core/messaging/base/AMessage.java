package org.rmatil.sync.core.messaging.base;

import org.rmatil.sync.core.messaging.StatusCode;

import java.io.Serializable;
import java.util.UUID;

/**
 * The common base class for a {@link ARequest} and
 * a {@link AResponse}
 */
public abstract class AMessage implements Serializable {

    protected UUID exchangeId;

    protected StatusCode statusCode;

    /**
     * @param exchangeId The exchangeId of the message
     * @param statusCode The status code of the message
     */
    protected AMessage(UUID exchangeId, StatusCode statusCode) {
        this.exchangeId = exchangeId;
        this.statusCode = statusCode;
    }

    public UUID getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(UUID exchangeId) {
        this.exchangeId = exchangeId;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(StatusCode statusCode) {
        this.statusCode = statusCode;
    }
}
