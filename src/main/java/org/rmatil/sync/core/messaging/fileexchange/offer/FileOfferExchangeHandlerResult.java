package org.rmatil.sync.core.messaging.fileexchange.offer;

import java.util.List;

/**
 * Describes the result of a file offering
 */
public class FileOfferExchangeHandlerResult {

    /**
     * A list of all received responses
     */
    List<FileOfferResponse> fileOfferResponses;

    /**
     * @param fileOfferResponse A list of all responses
     */
    public FileOfferExchangeHandlerResult(List<FileOfferResponse> fileOfferResponse) {
        this.fileOfferResponses = fileOfferResponse;
    }

    /**
     * Returns a list of all responses
     *
     * @return The received responses
     */
    public List<FileOfferResponse> getFileOfferResponses() {
        return fileOfferResponses;
    }
}
