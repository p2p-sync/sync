package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;

import java.util.Map;
import java.util.UUID;

/**
 * Send this offer result request to all clients of a file Exchange to notify them
 * about the result of the previous executed conflict negotiation.
 *
 * @see org.rmatil.sync.core.messaging.fileexchange.FileExchangeHandler
 */
public class FileOfferResultRequest implements IRequest {

    /**
     * The file exchange id
     */
    protected UUID fileExchangeId;

    /**
     * The client device which is sending this request
     */
    protected ClientDevice clientDevice;

    /**
     * A map having as key the conflict file path and as corresponding value
     * the peer address where to fetch that file
     */
    protected Map<String, PeerAddress> conflictFiles;

    /**
     * @param fileExchangeId The file exchange id
     * @param clientDevice The client device which is sending this request
     * @param conflictFiles A map having as key the conflict file path and as corresponding value the peer address where to fetch that file
     */
    public FileOfferResultRequest(UUID fileExchangeId, ClientDevice clientDevice, Map<String, PeerAddress> conflictFiles) {
        this.fileExchangeId = fileExchangeId;
        this.clientDevice = clientDevice;
        this.conflictFiles = conflictFiles;
    }

    /**
     * Returns the conflict files, i.e. a map having as
     * key the conflict file path and as corresponding value
     * the peer address where to fetch that file
     *
     * @return The conflict files
     */
    public Map<String, PeerAddress> getConflictFiles() {
        return conflictFiles;
    }

    @Override
    public UUID getExchangeId() {
        return this.fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
