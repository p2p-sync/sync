package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import java.util.List;

/**
 * The result of the {@link FetchObjectStoreExchangeHandler}.
 * Contains all fetched object stores of all other clients.
 */
public class FetchObjectStoreExchangeHandlerResult {

    /**
     * All fetched object stores
     */
    protected List<FetchObjectStoreResponse> responses;

    /**
     * @param responses All fetched object stores
     */
    public FetchObjectStoreExchangeHandlerResult(List<FetchObjectStoreResponse> responses) {
        this.responses = responses;
    }

    /**
     * Returns all fetched object stores
     *
     * @return All fetched object stores
     */
    public List<FetchObjectStoreResponse> getResponses() {
        return responses;
    }
}
