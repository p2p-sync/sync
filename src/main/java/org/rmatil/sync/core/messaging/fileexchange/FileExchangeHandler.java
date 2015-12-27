package org.rmatil.sync.core.messaging.fileexchange;

import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.core.messaging.ANetworkHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponse;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Implements the crucial step of deciding whether a conflict
 * file has to be created locally or not.
 * <p>
 * All clients will then be informed of the result.
 */
public class FileExchangeHandler extends ANetworkHandler<FileExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileExchangeHandler.class);

    /**
     * The id of the file exchange
     */
    protected UUID fileExchangeId;

    public FileExchangeHandler(UUID fileExchangeId, IUser user, ClientManager clientManager, IClient client, FileOfferRequest fileOfferRequest) {
        super(user, clientManager, client, fileOfferRequest);
        this.fileExchangeId = fileExchangeId;
    }

    @Override
    public FileExchangeHandlerResult handleResult()
            throws SyncFailedException {

        // we create a conflict file for our client if at least one client has another version
        boolean inConsense = true;
        for (Map.Entry<ClientDevice, FileOfferResponse> responseEntry : this.respondedClients.entrySet()) {
            if (responseEntry.getValue().hasConflict()) {
                logger.info("Client " + responseEntry.getValue().getClientDevice().getClientDeviceId() + " (Address: " + responseEntry.getValue().getClientDevice().getPeerAddress().inetAddress().getHostAddress() + " had detected a conflict.");
                inConsense = false;

                // actually, we could break here but then we would lost the log entry
                // which clients do have a conflict
            }
        }

        if (! inConsense) {
            // TODO: create conflict file
        }


        // Notify all clients about the result

        // TODO: return the conflict file event
        return null;
    }

}
