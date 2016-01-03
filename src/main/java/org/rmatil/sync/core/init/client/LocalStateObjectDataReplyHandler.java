package org.rmatil.sync.core.init.client;

import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IResponseCallback;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.util.Map;
import java.util.UUID;

public class LocalStateObjectDataReplyHandler extends ObjectDataReplyHandler {

    protected IStorageAdapter storageAdapter;
    protected IObjectStore objectStore;

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, IClient client, Map<UUID, IResponseCallback> callbackHandlers) {
        super(client, callbackHandlers);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
    }

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, IClient client) {
        super(client);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
    }

    public void setClient(IClient client) {
        super.client = client;
    }

    public Object reply(PeerAddress sender, Object request) throws Exception {
        if (request instanceof FileOfferRequest) {
            ((FileOfferRequest) request).setStorageAdapter(this.storageAdapter);
            ((FileOfferRequest) request).setObjectStore(this.objectStore);
        }

        return super.reply(sender, request);
    }
}
