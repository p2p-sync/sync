package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.network.api.*;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LocalStateObjectDataReplyHandler extends ObjectDataReplyHandler {

    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;
    protected MBassador       globalEventBus;
    protected Map<String, Set<UUID>> pathsInProgess = new HashMap<>();

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

    /**
     * Add a response callback for a particular file.
     * This ensures that if a file offer request is received for the same file
     * as edited in this registered exchange, it will be denied.
     *
     * Contrary, {@link ObjectDataReplyHandler#removeResponseCallbackHandler(UUID)} can not
     * ensure this protection.
     *
     * @param requestExchangeId The exchange id of the request to add the callback handler
     * @param responseCallback The callback handler to add
     */
    @Override
    public void addResponseCallbackHandler(UUID requestExchangeId, IResponseCallback responseCallback) {
        super.addResponseCallbackHandler(requestExchangeId, responseCallback);

        if (responseCallback instanceof ILocalStateResponseCallback) {
            for (String entry :((ILocalStateResponseCallback) responseCallback).getAffectedFilePaths()) {
                Set<UUID> exchangesInProgress = this.pathsInProgess.get(entry);

                if (null == exchangesInProgress) {
                    exchangesInProgress = new HashSet<>();
                }

                exchangesInProgress.add(requestExchangeId);
            }
        }
    }

    /**
     * Remove a response callback for a particular file.
     * This ensures that if a file offer request is received for the same file
     * as edited in this registered exchange, it will be denied.
     *
     * Contrary, {@link ObjectDataReplyHandler#removeResponseCallbackHandler(UUID)} can not
     * ensure this protection.
     *
     * @param requestExchangeId The exchange id of the request to remove the callback handler
     */
    public void removeResponseCallbackHandler(UUID requestExchangeId) {
        super.removeResponseCallbackHandler(requestExchangeId);

        for (Map.Entry<String, Set<UUID>> entry : this.pathsInProgess.entrySet()) {
            Set<UUID> exchangeIdsInProgress = entry.getValue();

            if (null != exchangeIdsInProgress && exchangeIdsInProgress.contains(requestExchangeId)) {
                exchangeIdsInProgress.remove(requestExchangeId);
            }
        }
    }

    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        // forward the request to the correct data reply instance
        if (request instanceof IRequest) {

            // check if any other exchange is in progress
            if (request instanceof FileOfferRequest &&
                    ! this.exchangeInProgress((FileOfferRequest) request) &&
                    this.affectedFileIsInProgress((FileOfferRequest) request)) {

                logger.error("There are already exchanges in progress for the file affected by request " + ((IRequest) request).getExchangeId() + ". Returning a unaccepted file offer response");

                this.client.sendDirect(
                        ((IRequest) request).getClientDevice().getPeerAddress(),
                        new FileOfferResponse(
                                ((IRequest) request).getExchangeId(),
                                new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                                new ClientLocation(
                                        ((IRequest) request).getClientDevice().getClientDeviceId(),
                                        ((IRequest) request).getClientDevice().getPeerAddress()
                                ),
                                false,
                                false
                        )
                );

                return null;
            }

            if (this.requestCallbackHandlers.containsKey(request.getClass())) {
                logger.debug("Using " + this.requestCallbackHandlers.get(request.getClass()).getName() + " as handler for request " + ((IRequest) request).getExchangeId());
                Class<? extends IRequestCallback> requestCallbackClass = this.requestCallbackHandlers.get(request.getClass());

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

    protected boolean exchangeInProgress(FileOfferRequest request) {
        String relativePath = request.getEvent().getPath();

        Set<UUID> exchangesInProgress = this.pathsInProgess.get(relativePath);

        if (null != exchangesInProgress && exchangesInProgress.contains(request.getExchangeId())) {
            // exchange is still in progress, we allow incoming requests for this exchange
            return true;
        }

        // exchange is not registered
        return false;
    }

    protected boolean affectedFileIsInProgress(FileOfferRequest request) {
        String relativePath = request.getEvent().getPath();

        Set<UUID> exchangesInProgress = this.pathsInProgess.get(relativePath);

        while (null == exchangesInProgress) {
            Path path = Paths.get(relativePath);

            path = path.subpath(0, Math.max(1, path.getNameCount() - 1));

            exchangesInProgress = this.pathsInProgess.get(path.getFileName().toString());

            // only the top level is left
            if (1 == path.getNameCount()) {
                break;
            }
        }

        // if there are any exchanges in progress, affected file is in progress
        return null != exchangesInProgress;
    }
}
