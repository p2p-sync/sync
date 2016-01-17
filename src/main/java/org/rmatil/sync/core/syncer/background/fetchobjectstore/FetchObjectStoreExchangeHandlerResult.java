package org.rmatil.sync.core.syncer.background.fetchobjectstore;

import java.util.List;

public class FetchObjectStoreExchangeHandlerResult {

    protected List<FetchObjectStoreResponse> responses;

    public FetchObjectStoreExchangeHandlerResult(List<FetchObjectStoreResponse> responses) {
        this.responses = responses;
    }

    public List<FetchObjectStoreResponse> getResponses() {
        return responses;
    }
}
