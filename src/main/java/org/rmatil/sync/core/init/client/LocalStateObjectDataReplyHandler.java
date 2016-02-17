package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandResponse;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.*;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class LocalStateObjectDataReplyHandler extends ObjectDataReplyHandler {

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected MBassador<IBusEvent> globalEventBus;
    protected IEventAggregator     eventAggregator;
    protected INodeManager         nodeManager;
    protected IAccessManager       accessManager;

    protected Map<String, Set<UUID>> pathsInProgress = new HashMap<>();

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, INode node, MBassador<IBusEvent> globalEventBus, IEventAggregator eventAggregator, INodeManager nodeManager, IAccessManager accessManager, Map<UUID, IResponseCallback> responseCallbackHandlers, Map<Class<? extends IRequest>, Class<? extends IRequestCallback>> requestCallbackHandlers) {
        super(node, responseCallbackHandlers, requestCallbackHandlers);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;
        this.eventAggregator = eventAggregator;
        this.nodeManager = nodeManager;
        this.accessManager = accessManager;
    }

    public LocalStateObjectDataReplyHandler(IStorageAdapter storageAdapter, IObjectStore objectStore, INode client, MBassador<IBusEvent> globalEventBus, IEventAggregator eventAggregator, INodeManager nodeManager, IAccessManager accessManager) {
        super(client);
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
        this.globalEventBus = globalEventBus;
        this.eventAggregator = eventAggregator;
        this.nodeManager = nodeManager;
        this.accessManager = accessManager;
    }

    public void setNode(INode client) {
        super.node = client;
    }

    public void setEventAggregator(IEventAggregator eventAggregator) {
        this.eventAggregator = eventAggregator;
    }

    public void setNodeManager(INodeManager nodeManager) {
        this.nodeManager = nodeManager;
    }

    /**
     * Add a response callback for a particular file.
     * This ensures that if a file offer request is received for the same file
     * as edited in this registered exchange, it will be denied.
     * <p>
     * Contrary, {@link ObjectDataReplyHandler#removeResponseCallbackHandler(UUID)} can not
     * ensure this protection.
     *
     * @param requestExchangeId The exchange id of the request to add the callback handler
     * @param responseCallback  The callback handler to add
     */
    @Override
    public void addResponseCallbackHandler(UUID requestExchangeId, IResponseCallback responseCallback) {
        super.addResponseCallbackHandler(requestExchangeId, responseCallback);

        if (responseCallback instanceof ILocalStateResponseCallback) {
            for (String entry : ((ILocalStateResponseCallback) responseCallback).getAffectedFilePaths()) {
                Set<UUID> exchangesInProgress = this.pathsInProgress.get(entry);

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
     * <p>
     * Contrary, {@link ObjectDataReplyHandler#removeResponseCallbackHandler(UUID)} can not
     * ensure this protection.
     *
     * @param requestExchangeId The exchange id of the request to remove the callback handler
     */
    public void removeResponseCallbackHandler(UUID requestExchangeId) {
        super.removeResponseCallbackHandler(requestExchangeId);

        for (Map.Entry<String, Set<UUID>> entry : this.pathsInProgress.entrySet()) {
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

            if (request instanceof FileOfferRequest) {
                FileOfferRequest fileOfferRequest = (FileOfferRequest) request;

                if (! this.exchangeInProgress(fileOfferRequest.getExchangeId(), fileOfferRequest.getEvent().getPath()) &&
                        this.affectedFileIsInProgress(fileOfferRequest.getEvent().getPath())) {

                    logger.error("There are already exchanges in progress for the file affected by offer request " + ((IRequest) request).getExchangeId() + ". Returning a denied file offer response");

                    this.node.sendDirect(
                            ((IRequest) request).getClientDevice().getPeerAddress(),
                            new FileOfferResponse(
                                    ((IRequest) request).getExchangeId(),
                                    StatusCode.DENIED,
                                    new ClientDevice(this.node.getUser().getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress()),
                                    new NodeLocation(
                                            ((IRequest) request).getClientDevice().getClientDeviceId(),
                                            ((IRequest) request).getClientDevice().getPeerAddress()
                                    )
                            )
                    );

                    return null;
                }

            } else if (request instanceof FileDemandRequest) {
                FileDemandRequest fileDemandRequest = (FileDemandRequest) request;

                if (! this.exchangeInProgress(fileDemandRequest.getExchangeId(), fileDemandRequest.getRelativeFilePath()) &&
                        this.affectedFileIsInProgress(fileDemandRequest.getRelativeFilePath())) {

                    logger.error("There are already exchanges in progress for the file affected by demand request " + ((IRequest) request).getExchangeId() + ". Returning a denied file demand response");

                    this.node.sendDirect(
                            ((IRequest) request).getClientDevice().getPeerAddress(),
                            new FileDemandResponse(
                                    ((IRequest) request).getExchangeId(),
                                    StatusCode.DENIED,
                                    new ClientDevice(this.node.getUser().getUserName(), this.node.getClientDeviceId(), this.node.getPeerAddress()),
                                    null,
                                    ((FileDemandRequest) request).getRelativeFilePath(),
                                    true,
                                    - 1,
                                    - 1,
                                    - 1,
                                    - 1,
                                    null,
                                    new NodeLocation(
                                            ((IRequest) request).getClientDevice().getClientDeviceId(),
                                            ((IRequest) request).getClientDevice().getPeerAddress()
                                    ),
                                    null
                            )
                    );

                    return null;
                }
            }

            if (this.requestCallbackHandlers.containsKey(request.getClass())) {
                logger.debug("Using " + this.requestCallbackHandlers.get(request.getClass()).getName() + " as handler for request " + ((IRequest) request).getExchangeId());
                Class<? extends IRequestCallback> requestCallbackClass = this.requestCallbackHandlers.get(request.getClass());

                if (IExtendedLocalStateRequestCallback.class.isAssignableFrom(requestCallbackClass)) {
                    // create a new instance running in its own thread
                    IExtendedLocalStateRequestCallback requestCallback = (IExtendedLocalStateRequestCallback) requestCallbackClass.newInstance();
                    requestCallback.setNode(this.node);
                    requestCallback.setStorageAdapter(this.storageAdapter);
                    requestCallback.setObjectStore(this.objectStore);
                    requestCallback.setGlobalEventBus(this.globalEventBus);
                    requestCallback.setRequest((IRequest) request);
                    requestCallback.setEventAggregator(this.eventAggregator);
                    requestCallback.setClientManager(this.nodeManager);
                    requestCallback.setAccessManager(this.accessManager);

                    Thread thread = new Thread(requestCallback);
                    thread.setName("RequestCallback-" + ((IRequest) request).getExchangeId());
                    thread.start();

                    return null;
                }

                if (ILocalStateRequestCallback.class.isAssignableFrom(requestCallbackClass)) {
                    // create a new instance running in its own thread
                    ILocalStateRequestCallback requestCallback = (ILocalStateRequestCallback) requestCallbackClass.newInstance();
                    requestCallback.setNode(this.node);
                    requestCallback.setStorageAdapter(this.storageAdapter);
                    requestCallback.setObjectStore(this.objectStore);
                    requestCallback.setGlobalEventBus(this.globalEventBus);
                    requestCallback.setRequest((IRequest) request);
                    requestCallback.setAccessManager(this.accessManager);

                    Thread thread = new Thread(requestCallback);
                    thread.setName("RequestCallback-" + ((IRequest) request).getExchangeId());
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

    protected boolean exchangeInProgress(UUID exchangeId, String relativePath) {
        Set<UUID> exchangesInProgress = this.pathsInProgress.get(relativePath);

        if (null != exchangesInProgress && exchangesInProgress.contains(exchangeId)) {
            // exchange is still in progress, we allow incoming requests for this exchange
            return true;
        }

        // exchange is not registered
        return false;
    }

    protected boolean affectedFileIsInProgress(String relativePath) {
        Set<UUID> exchangesInProgress = this.pathsInProgress.get(relativePath);

        Path path = Paths.get(relativePath);
        while (null == exchangesInProgress) {

            path = path.subpath(0, Math.max(1, path.getNameCount() - 1));

            exchangesInProgress = this.pathsInProgress.get(path.getFileName().toString());

            // only the top level is left
            if (1 == path.getNameCount()) {
                break;
            }
        }

        // if there are any exchanges in progress, affected file is in progress
        return null != exchangesInProgress;
    }
}
