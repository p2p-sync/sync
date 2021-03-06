package org.rmatil.sync.core.messaging.sharingexchange.unshared;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnsharedRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(UnsharedRequestHandler.class);

    /**
     * The storage adapter to access the synced folder
     */
    protected ITreeStorageAdapter storageAdapter;

    /**
     * The object store
     */
    protected IObjectStore objectStore;

    /**
     * The client to send responses
     */
    protected INode node;

    /**
     * The unshared request which have been received
     */
    protected UnsharedRequest request;

    /**
     * The global event bus to send events to
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The access manager to check for sharer's access to files
     */
    protected IAccessManager accessManager;

    @Override
    public void setStorageAdapter(ITreeStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setNode(INode INode) {
        this.node = INode;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof UnsharedRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + UnsharedRequest.class.getName());
        }

        this.request = (UnsharedRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // add file id from request to the attached file path
            logger.info("Starting to unshare file for id " + this.request.getFileId() + " with sharer " + this.request.getSharer());

            String fileName = this.node.getIdentifierManager().getKey(this.request.getFileId());

            logger.debug("Found file " + fileName + " for fileId " + this.request.getFileId());

            PathObject sharedObject = this.objectStore.getObjectManager().getObjectForPath(
                    fileName
            );

            logger.trace("Found file on path " + sharedObject.getAbsolutePath() + " for file with id " + this.request.getFileId());

            this.objectStore.getSharerManager().removeSharer(
                    this.request.getSharer(),
                    sharedObject.getAbsolutePath()
            );

            this.sendResponse(StatusCode.ACCEPTED);

        } catch (Exception e) {
            logger.error("Got exception in UnsharedRequestHandler. Message: " + e.getMessage(), e);

            try {
                this.sendResponse(StatusCode.ERROR);
            } catch (Exception e1) {
                logger.error("Failed to notify originating node about error in exchange " + this.request.getExchangeId() + ". Message: " + e1.getMessage(), e1);
            }
        }
    }

    /**
     * Sends a response with the given status code back to the requesting client
     *
     * @param statusCode The status code to use in the response
     */
    public void sendResponse(StatusCode statusCode) {
        NodeLocation receiver = new NodeLocation(
                this.request.getClientDevice().getUserName(),
                this.request.getClientDevice().getClientDeviceId(),
                this.request.getClientDevice().getPeerAddress()
        );

        IResponse response = new UnsharedResponse(
                this.request.getExchangeId(),
                statusCode,
                new ClientDevice(
                        this.node.getUser().getUserName(),
                        this.node.getClientDeviceId(),
                        this.node.getPeerAddress()
                ),
                receiver
        );

        this.node.sendDirect(receiver, response);
    }
}
