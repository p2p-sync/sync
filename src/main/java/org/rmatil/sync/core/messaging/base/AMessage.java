package org.rmatil.sync.core.messaging.base;

import org.rmatil.sync.core.messaging.StatusCode;

import java.io.Serializable;
import java.util.UUID;

/**
 * The common base class for a {@link ARequest} and
 * a {@link AResponse}
 */
public abstract class AMessage implements Serializable {

    private static final long serialVersionUID = - 4409384758347550524L;

    /**
     * The exchange to which this message belongs
     */
    protected UUID exchangeId;

    /**
     * The status code of the message
     */
    protected StatusCode statusCode;

    /**
     * @param exchangeId The exchangeId of the message
     * @param statusCode The status code of the message
     */
    protected AMessage(UUID exchangeId, StatusCode statusCode) {
        this.exchangeId = exchangeId;
        this.statusCode = statusCode;
    }

    /**
     * Get the exchange id to which this message belongs
     *
     * @return The exchange id
     */
    public UUID getExchangeId() {
        return exchangeId;
    }

    /**
     * Set the exchange id to which this message belongs
     *
     * @param exchangeId The exchange id
     */
    public void setExchangeId(UUID exchangeId) {
        this.exchangeId = exchangeId;
    }

    /**
     * Get the status code of this message
     *
     * @return The status code of this message
     */
    public StatusCode getStatusCode() {
        return statusCode;
    }

    /**
     * Set the status code of this message
     *
     * @param statusCode The status code
     */
    public void setStatusCode(StatusCode statusCode) {
        this.statusCode = statusCode;
    }
}
