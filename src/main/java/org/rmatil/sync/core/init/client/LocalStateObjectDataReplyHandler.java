package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.network.api.*;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.util.Map;
import java.util.UUID;

public class LocalStateObjectDataReplyHandler extends ObjectDataReplyHandler {

    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;
    protected MBassador       globalEventBus;

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, IClient client, MBassador globalEventBus, Map<UUID, IResponseCallback> responseCallbackHandlers, Map<Class<? extends IRequest>, Class<? extends IRequestCallback>> requestCallbackHandlers) {
        super(client, responseCallbackHandlers, requestCallbackHandlers);
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

    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        // forward the request to the correct data reply instance
        if (request instanceof IRequest) {
            if (this.requestCallbackHandlers.containsKey(request.getClass())) {
                logger.debug("Using " + this.requestCallbackHandlers.get(request.getClass()).getName() + " as handler for request " + ((IRequest) request).getExchangeId());
                Class<? extends IRequestCallback> requestCallbackClass = this.requestCallbackHandlers.get(request.getClass());

                // TODO: check this
                if (requestCallbackClass.getClass().isInstance(ILocalStateRequestCallback.class)) {
                    // create a new instance running in its own thread
                    ILocalStateRequestCallback requestCallback = (ILocalStateRequestCallback) requestCallbackClass.newInstance();
                    requestCallback.setClient(this.client);
                    requestCallback.setStorageAdapter(this.storageAdapter);
                    requestCallback.setObjectStore(this.objectStore);
                    requestCallback.setGlobalEventBus(this.globalEventBus);
                    requestCallback.setRequest((IRequest) request);

                    requestCallback.setRequest((IRequest) request);

                    Thread thread = new Thread(requestCallback);
                    thread.setName("RequestCallback for request " + ((IRequest) request).getExchangeId());
                    thread.start();


                    return null;
                }

                return super.reply(sender, request);
            }
        }

        // if we receive a response, we forward it to the correct callback handler
        if (request instanceof IResponse) {
            if (this.responseCallbackHandlers.containsKey(((IResponse) request).getExchangeId())) {
                IResponseCallback responseCallback = this.responseCallbackHandlers.get(((IResponse) request).getExchangeId());
                logger.debug("Using " + responseCallback.getClass().getName() + " as handler for response " + ((IResponse) request).getExchangeId());

                responseCallback.onResponse((IResponse) request);

                return null;
            }
        }

        logger.warn("No appropriate object data reply instance found for request " + request.getClass().getName() + ". Sending NULL as response!");
        return null;
    }
}
