package org.rmatil.sync.core.messaging.sharingexchange.unshare;

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
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class UnshareRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(UnshareRequestHandler.class);

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
     * The unshare request which have been received
     */
    protected UnshareRequest request;

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
        if (! (iRequest instanceof UnshareRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + UnshareRequest.class.getName());
        }

        this.request = (UnshareRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // add file id from request to the attached file path
            logger.info("Unsharing file for id " + this.request.getFileId());

            PathObject sharedObject = this.objectStore.getObjectManager().getObjectForPath(
                    this.node.getIdentifierManager().getKey(this.request.getFileId())
            );

            logger.info("Found file on path " + sharedObject.getAbsolutePath() + " for file with id " + this.request.getFileId());

            // reset values in case this gets shared again
            // avoiding a delete sync back to the owner
            sharedObject.setSharers(new HashSet<>());
            sharedObject.setIsShared(false);
            sharedObject.setAccessType(null);
            sharedObject.setOwner(null);

            this.objectStore.getObjectManager().writeObject(sharedObject);

            // remove the file
            this.storageAdapter.delete(new TreePathElement(sharedObject.getAbsolutePath()));

            this.sendResponse(StatusCode.ACCEPTED);
        } catch (Exception e) {
            logger.error("Got exception in UnshareRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a response with the given status code back to the requesting client
     *
     * @param statusCode The status code of the response
     */
    protected void sendResponse(StatusCode statusCode) {
        if (null == this.node) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        NodeLocation receiver = new NodeLocation(
                this.request.getClientDevice().getUserName(),
                this.request.getClientDevice().getClientDeviceId(),
                this.request.getClientDevice().getPeerAddress()
        );

        IResponse response = new UnshareResponse(
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
