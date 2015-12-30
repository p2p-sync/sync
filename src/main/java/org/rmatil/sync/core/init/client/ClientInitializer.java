package org.rmatil.sync.core.init.client;

import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.model.RemoteClientLocation;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.config.Config;
import org.rmatil.sync.network.core.Client;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.core.dht.DhtStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;

import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.UUID;

public class ClientInitializer implements IInitializer<IClient> {

    protected int port;

    protected IUser   user;
    protected IClient client;

    protected DhtStorageAdapter dhtStorageAdapter;
    protected ClientManager     clientManager;
    protected Config            networkConfig;

    protected RemoteClientLocation bootstrapLocation;

    protected Map<Class, ObjectDataReply> objectDataReplyHandler;

    public ClientInitializer(Map<Class, ObjectDataReply> objectDataReplyHandler, IUser user, int port, RemoteClientLocation bootstrapLocation) {
        this.objectDataReplyHandler = objectDataReplyHandler;
        this.user = user;
        this.port = port;
        this.bootstrapLocation = bootstrapLocation;
    }

    @Override
    public IClient init()
            throws InitializationException {
        // TODO: generate KeyPair globally in Sync and inject it from there
        // TODO: generate keyPair only, if none exists yet in the DHT. Use the existing one if possible
        // TODO: save KeyPair to local sync folder to regenerate user profile if all clients were offline or regenerate a new one

        networkConfig = Config.IPv4;
        networkConfig.setPort(this.port);

        this.client = new Client(networkConfig, this.user, UUID.randomUUID());

        // Set object reply handlers which handle direct requests to the peer, i.e. the client
        this.client.setObjectDataReplyHandler(
                new ObjectDataReplyHandler(this.objectDataReplyHandler)
        );

        return this.client;
    }

    @Override
    public void start()
            throws InitializationStartException {

        // start a peer
        boolean isSuccess;
        if (null == this.bootstrapLocation) {
            isSuccess = this.client.start();
        } else {
            isSuccess = this.client.start(this.bootstrapLocation.getIpAddress(), this.bootstrapLocation.getPort());
        }

        if (! isSuccess) {
            throw new InitializationStartException("Could not start client");
        }

        // we can init the dht storage adapter only after
        // the peerDHT is started (i.e. built), otherwise we will
        // get a NullPointerException on the private/public key for protection
        this.dhtStorageAdapter = new DhtStorageAdapter(this.client.getPeerDht());
        this.clientManager = new ClientManager(
                this.dhtStorageAdapter,
                networkConfig.getLocationsContentKey(),
                networkConfig.getPrivateKeyContentKey(),
                networkConfig.getPublicKeyContentKey(),
                networkConfig.getDomainKey()
        );

        try {
            this.clientManager.addPrivateKey(this.user);
            this.clientManager.addPublicKey(this.user);
        } catch (InputOutputException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop()
            throws InitializationStopException {

        try {
            this.clientManager.removeClientLocation(this.user, new ClientLocation(
                    this.client.getClientDeviceId(),
                    this.client.getPeerAddress()
            ));
        } catch (InputOutputException e) {
            throw new InitializationStopException(e);
        }

        this.client.shutdown();
    }
}
