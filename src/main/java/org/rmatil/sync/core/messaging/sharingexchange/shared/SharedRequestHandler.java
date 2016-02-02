package org.rmatil.sync.core.messaging.sharingexchange.shared;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushRequest;
import org.rmatil.sync.core.messaging.sharingexchange.share.ShareRequest;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(SharedRequestHandler.class);

    /**
     * The storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The object store to access versions
     */
    protected IObjectStore objectStore;

    /**
     * The client to send back messages
     */
    protected IClient client;

    /**
     * The file shared request from the sender
     */
    protected SharedRequest request;

    /**
     * The global event bus to add ignore events
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * The access manager to check for sharer's access to files
     */
    protected IAccessManager accessManager;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
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
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    @Override
    public void setAccessManager(IAccessManager accessManager) {
        this.accessManager = accessManager;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof SharedRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + SharedRequest.class.getName());
        }

        this.request = (SharedRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            // add file id from request to the attached file path
            logger.info("Adding sharer " + this.request.getSharer() + " (AccessType " + this.request.getAccessType() + ") to path " + this.request.getRelativePath());

            this.objectStore.getSharerManager().addSharer(
                    this.request.getSharer(),
                    this.request.getAccessType(),
                    this.request.getRelativePath()
            );

            // if there is no owner of the file yet (due to sharing already, or
            // if we are not the owner, but just share the shared file with any other user)
            // set it to our self
            if (null == this.objectStore.getSharerManager().getOwner(this.request.getRelativePath())) {
                this.objectStore.getSharerManager().addOwner(
                        this.client.getUser().getUserName(),
                        this.request.getRelativePath()
                );
            }

            this.sendResponse(true);

        } catch (Exception e) {
            logger.error("Got exception in SharedRequestHandler. Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the given response back to the client
     *
     * @param hasAccepted Whether the client has accepted the shared request
     */
    public void sendResponse(boolean hasAccepted) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        IResponse response = new SharedResponse(
                this.request.getExchangeId(),
                new ClientDevice(
                        this.client.getUser().getUserName(),
                        this.client.getClientDeviceId(),
                        this.client.getPeerAddress()
                ),
                new ClientLocation(
                        this.request.getClientDevice().getClientDeviceId(),
                        this.request.getClientDevice().getPeerAddress()
                ),
                hasAccepted
        );

        this.client.sendDirect(response.getReceiverAddress().getPeerAddress(), response);
    }
}
