package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IResponseCallback;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.util.Map;
import java.util.UUID;

public class LocalStateObjectDataReplyHandler extends ObjectDataReplyHandler {

    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;
    protected MBassador       globalEventBus;

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, IClient client, MBassador globalEventBus, Map<UUID, IResponseCallback> callbackHandlers) {
        super(client, callbackHandlers);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;
    }

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, IClient client, MBassador globalEventBus) {
        super(client);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;
    }

    public void setClient(IClient client) {
        super.client = client;
    }

    public Object reply(PeerAddress sender, Object request) throws Exception {
        if (request instanceof FileOfferRequest) {
            ((FileOfferRequest) request).setStorageAdapter(this.storageAdapter);
            ((FileOfferRequest) request).setObjectStore(this.objectStore);
            ((FileOfferRequest) request).setGlobalEventBus(this.globalEventBus);
        } else if (request instanceof FilePushRequest) {
            ((FilePushRequest) request).setStorageAdapter(this.storageAdapter);
            ((FilePushRequest) request).setObjectStore(this.objectStore);
            ((FilePushRequest) request).setGlobalEventBus(this.globalEventBus);
        }

        return super.reply(sender, request);
    }
}
