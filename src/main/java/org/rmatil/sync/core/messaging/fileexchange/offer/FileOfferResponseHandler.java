package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.messaging.fileexchange.FileExchangeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The response handler which forwards incoming FileOfferResponses
 * to the correct file exchange handler based on the file exchange id of
 * the request resp. response.
 *
 * @see FileExchangeHandler
 * @see FileOfferResponse
 */
public class FileOfferResponseHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferResponseHandler.class);

    /**
     * The list of registered file exchange handler
     */
    protected Map<UUID, FileExchangeHandler> fileExchangeHandler;

    public FileOfferResponseHandler() {
        this.fileExchangeHandler = new HashMap<>();
    }

    /**
     * Registers the given exchange handler for the given file exchange id
     *
     * @param fileExchangeId The file exchange id to register the handler to
     * @param fileExchangeHandler The handler to register
     */
    public void registerFileExchangeHandler(UUID fileExchangeId, FileExchangeHandler fileExchangeHandler) {
        this.fileExchangeHandler.put(fileExchangeId, fileExchangeHandler);
    }

    /**
     * Unregisters the file exchange handler for the given file exchange id
     *
     * @param fileExchangeId The file exchange id of which to unregister the file exchange handler
     */
    public void unregisterFileExchangeHandler(UUID fileExchangeId) {
        this.fileExchangeHandler.remove(fileExchangeId);
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FileOfferResponse)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        // assign file response to correct file exchange handler
        FileExchangeHandler fileExchangeHandler = this.fileExchangeHandler.get(((FileOfferResponse) request).getExchangeId());

        if (null == fileExchangeHandler) {
            logger.error("Failed to add response for fileExchangeId " + ((FileOfferResponse) request).getExchangeId() + ": No FileExchangeHandler found for this fileExchange!");
            return null;
        }

        fileExchangeHandler.addResponse((FileOfferResponse) request);

        return null;
    }
}
