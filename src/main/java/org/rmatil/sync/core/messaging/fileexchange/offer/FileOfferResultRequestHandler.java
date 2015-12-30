package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FileOfferResultRequestHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferResultRequestHandler.class);

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * @param storageAdapter The storage adapter for the synchronized folder
     */
    public FileOfferResultRequestHandler(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FileOfferResultRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        FileOfferResultRequest fileOfferResultRequest = (FileOfferResultRequest) request;

        if (fileOfferResultRequest.hasConflict()) {
            logger.debug("FileOfferResultRequest has a conflict");
            // then we request each conflict file for of the corresponding client
            for (Map.Entry<String, PeerAddress> entry : fileOfferResultRequest.getConflictFiles().entrySet()) {
                logger.info("Requesting conflict file " + entry.getKey() + " from client " + entry.getValue().inetAddress().getHostAddress() + ":" + entry.getValue().tcpPort());
                this.fetchFile(entry.getKey(), entry.getValue());
            }
        } else {
            // if not, we check, if we have that file, and request it if we do not
            for (Map.Entry<String, PeerAddress> entry : fileOfferResultRequest.getConflictFiles().entrySet()) {
                IPathElement pathElement = new LocalPathElement(entry.getKey());

                if (! this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                    logger.info("Requesting not existing file " + entry.getKey() + " from client " + entry.getValue().inetAddress().getHostAddress() + ":" + entry.getValue().tcpPort());
                    this.fetchFile(entry.getKey(), entry.getValue());
                }
            }
        }

        return null;
    }


    protected void fetchFile(String filePath, PeerAddress clientToFetchFrom) {

    }
}
