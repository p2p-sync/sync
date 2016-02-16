package org.rmatil.sync.core.init.client;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ConnectionConfiguration;
import org.rmatil.sync.network.core.Node;
import org.rmatil.sync.network.core.exception.ConnectionException;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;

import java.util.UUID;

public class ClientInitializer implements IInitializer<INode> {

    protected int                     port;
    protected IUser                   user;
    protected INode                   node;
    protected INodeManager            nodeManager;
    protected RemoteClientLocation    bootstrapLocation;
    protected ObjectDataReplyHandler  objectDataReplyHandler;
    protected ConnectionConfiguration connectionConfiguration;

    public ClientInitializer(ConnectionConfiguration connectionConfiguration, ObjectDataReplyHandler objectDataReplyHandler, IUser user, RemoteClientLocation bootstrapLocation) {
        this.connectionConfiguration = connectionConfiguration;
        this.objectDataReplyHandler = objectDataReplyHandler;
        this.user = user;
        this.bootstrapLocation = bootstrapLocation;
    }

    @Override
    public INode init()
            throws InitializationException {
        UUID deviceId = UUID.randomUUID();

        this.node = new Node(this.connectionConfiguration, this.user, deviceId);

        // Set object reply handlers which handle direct requests to the peer, i.e. the client
        this.node.setObjectDataReplyHandler(
                objectDataReplyHandler
        );

        return this.node;
    }

    @Override
    public void start()
            throws InitializationStartException {

        // start a peer
        try {
            boolean isSuccess;
            if (null == this.bootstrapLocation) {
                isSuccess = this.node.start();
            } else {
                isSuccess = this.node.start(this.bootstrapLocation.getIpAddress(), this.bootstrapLocation.getPort());
            }

            if (! isSuccess) {
                throw new InitializationStartException("Could not start client");
            }

            this.nodeManager = this.node.getNodeManager();
        } catch (ConnectionException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop()
            throws InitializationStopException {

        this.node.shutdown();
    }


    /**
     * <p color="red">Only defined after starting is done</p>
     *
     * @return The client manager
     */
    public INodeManager getNodeManager() {
        return nodeManager;
    }

}
