package org.rmatil.sync.core.init.client;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.ConnectionConfiguration;
import org.rmatil.sync.network.core.exception.ConnectionException;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;

import java.util.UUID;

public class ClientInitializer implements IInitializer<IClient> {

    protected int                     port;
    protected IUser                   user;
    protected IClient                 client;
    protected IClientManager          clientManager;
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
    public IClient init()
            throws InitializationException {
        UUID deviceId = UUID.randomUUID();

        this.client = new Client(this.connectionConfiguration, this.user, deviceId);

        // Set object reply handlers which handle direct requests to the peer, i.e. the client
        this.client.setObjectDataReplyHandler(
                objectDataReplyHandler
        );

        return this.client;
    }

    @Override
    public void start()
            throws InitializationStartException {

        // start a peer
        try {
            boolean isSuccess;
            if (null == this.bootstrapLocation) {
                isSuccess = this.client.start();
            } else {
                isSuccess = this.client.start(this.bootstrapLocation.getIpAddress(), this.bootstrapLocation.getPort());
            }

            if (! isSuccess) {
                throw new InitializationStartException("Could not start client");
            }

            this.clientManager = this.client.getClientManager();
        } catch (ConnectionException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop()
            throws InitializationStopException {

        this.client.shutdown();
    }


    /**
     * <p color="red">Only defined after starting is done</p>
     *
     * @return The client manager
     */
    public IClientManager getClientManager() {
        return clientManager;
    }

}
