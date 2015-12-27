package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.model.ClientDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The request handler which decides how to
 * handle a file offer request. All other requests are handled by throwing an exception.
 *
 * @see FileOfferRequest
 */
public class FileOfferRequestHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferRequestHandler.class);

    /**
     * The client device of this handler
     */
    protected ClientDevice clientDevice;

    /**
     * @param clientDevice The client which handles the file offerings
     */
    public FileOfferRequestHandler(ClientDevice clientDevice) {
        this.clientDevice = clientDevice;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {


        if (! (request instanceof FileOfferRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        // TODO: check whether a different version exists locally
        boolean acceptedOffer = true;
        boolean hasConflict = false;

        return new FileOfferResponse(
                ((FileOfferRequest) request).getExchangeId(),
                this.clientDevice,
                acceptedOffer,
                hasConflict
        );
    }
}
