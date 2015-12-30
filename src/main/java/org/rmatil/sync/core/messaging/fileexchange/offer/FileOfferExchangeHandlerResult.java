package org.rmatil.sync.core.messaging.fileexchange.offer;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

import java.util.UUID;

/**
 * A wrapper for file exchange results
 *
 * @see FileOfferExchangeHandler#handleResult()
 */
public class FileOfferExchangeHandlerResult {

    /**
     * The id of the file exchange
     */
    private UUID fileExchangeId;

    /**
     * The file system event resulting in a file offering,
     * i.e. the event of the conflict file
     */
    private IEvent resultEvent;

    /**
     * @param fileExchangeId The id of the file exchange
     * @param resultEvent    The event resulting of the file exchange handling
     */
    public FileOfferExchangeHandlerResult(UUID fileExchangeId, IEvent resultEvent) {
        this.fileExchangeId = fileExchangeId;
        this.resultEvent = resultEvent;
    }

    /**
     * The id of the file exchange
     *
     * @return The file exchange id
     */
    public UUID getFileExchangeId() {
        return fileExchangeId;
    }

    /**
     * The event resulting by executing the file offering protocol
     *
     * @return The event or null
     *
     * @see FileOfferExchangeHandler#handleResult()
     */
    public IEvent getResultEvent() {
        return resultEvent;
    }
}
