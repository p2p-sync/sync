package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.version.api.IObjectManager;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

    protected IObjectStore objectStore;

    /**
     * @param clientDevice The client which handles the file offerings
     */
    public FileOfferRequestHandler(ClientDevice clientDevice, IObjectStore objectStore) {
        this.clientDevice = clientDevice;
        this.objectStore = objectStore;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {


        if (! (request instanceof FileOfferRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        PathObject pathObject = this.objectStore.getObjectManager().getObject(((FileOfferRequest) request).getRelativeFilePath());

        // compare local and remote file versions
        List<Version> localFileVersions = pathObject.getVersions();
        Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
        String lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

        List<Version> remoteFileVersions = pathObject.getVersions();
        Version lastRemoteFileVersion = remoteFileVersions.size() > 0 ? remoteFileVersions.get(remoteFileVersions.size() - 1) : null;
        String lastRemoteFileVersionHash = (null != lastRemoteFileVersion) ? lastRemoteFileVersion.getHash() : null;


        // we accept each offer for now
        boolean acceptedOffer = true;
        boolean hasConflict = false;

        // check whether a different version exists locally
        if ((null == lastLocalFileVersionHash || null == lastRemoteFileVersionHash) || (lastLocalFileVersionHash.equals(lastRemoteFileVersionHash))) {
            logger.info("Detected conflict for fileExchange "
                    + ((FileOfferRequest) request).getExchangeId()
                    + ": Remote version from client "
                    + ((FileOfferRequest) request).getClientDevice().getClientDeviceId()
                    + " was "
                    + ((lastRemoteFileVersionHash == null) ? "null" : lastRemoteFileVersionHash)
                    + ", local version was "
                    + ((lastLocalFileVersionHash == null) ? "null" : lastLocalFileVersionHash)
            );

            hasConflict = true;
        }

        return new FileOfferResponse(
                ((FileOfferRequest) request).getExchangeId(),
                this.clientDevice,
                acceptedOffer,
                hasConflict
        );
    }
}
