package org.rmatil.sync.core.messaging;

import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles communication between multiple clients.
 * Requires, that each client to which a request has been sent must respond with
 * the corresponding response to the configured request.
 * <p>
 * Once all clients responded, handleResult() will be invoked.
 * Note, that the concrete result computation is implemented by the extending class.
 *
 * @param <T> The result type which should be returned, once the information exchange between clients has been completed
 */
public abstract class ANetworkHandler<T> implements INetworkHandler<T> {

    private final static Logger logger = LoggerFactory.getLogger(ANetworkHandler.class);

    /**
     * The user of this client
     */
    protected IUser user;

    /**
     * The client manager to access meta information
     */
    protected ClientManager clientManager;

    /**
     * The client of this device
     */
    protected IClient client;

    /**
     * The initial request which has been sent to all clients
     */
    protected IRequest request;

    /**
     * A list of clients which to which the initial request has been sent
     * and are expected to respond
     */
    protected List<ClientDevice> notifiedClients;

    /**
     * A map having the client device which responded along with its response
     * representing all clients which have responded to the intial request
     */
    protected Map<ClientDevice, FileOfferResponse> respondedClients;

    /**
     * @param user          The user of this client
     * @param clientManager The client manager to access meta information
     * @param client        The client of this device
     * @param request       The initial request which will be sent to all clients
     */
    public ANetworkHandler(IUser user, ClientManager clientManager, IClient client, IRequest request) {
        this.user = user;
        this.clientManager = clientManager;
        this.client = client;
        this.request = request;

        this.notifiedClients = new ArrayList<>();
        this.respondedClients = new HashMap<>();
    }

    @Override
    public T call() {
        try {
            ClientDevice clientDevice = new ClientDevice(
                    this.user.getUserName(),
                    this.client.getClientDeviceId(),
                    this.client.getPeerAddress()
            );

            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(this.user);
            } catch (InputOutputException e) {
                throw new SyncFailedException("Could not fetch client locations to send file offer to. Message: " + e.getMessage(), e);
            }

            // offer file
            for (ClientLocation entry : clientLocations) {
                this.client.sendDirect(entry.getPeerAddress(), this.request);
                this.notifiedClients.add(clientDevice);
            }

            while (! this.allClientResponded()) {
                Thread.sleep(1000);
            }

            return this.handleResult();

        } catch (Exception e) {
            logger.error("Error in ANetworkHandler thread. Message: " + e.getMessage(), e);
        }

        return null;
    }

    @Override
    public void addResponse(IResponse response)
            throws SyncFailedException {

        if (! (response instanceof FileOfferResponse)) {
            throw new SyncFailedException("Expected response to be instance of FileOfferResponse. Got " + response.getClass());
        }

        logger.info("Got response of client " + response.getClientDevice().getClientDeviceId().toString());

        this.respondedClients.put(response.getClientDevice(), (FileOfferResponse) response);
    }

    @Override
    public boolean allClientResponded() {
        return this.respondedClients.keySet().containsAll(this.notifiedClients);
    }

    /**
     * Should be invoked once all protocol steps are completed.
     * Is called from call once all clients to which a request has been sent responded.
     *
     * @return The result after all protocol steps are performed. May be null
     *
     * @throws SyncFailedException If an error occurred during result computation
     */
    protected abstract T handleResult();

}
