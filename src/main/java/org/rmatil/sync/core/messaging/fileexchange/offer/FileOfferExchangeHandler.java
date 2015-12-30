package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.exception.SyncFailedException;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the crucial step of deciding whether a conflict
 * file has to be created locally or not.
 * <p>
 * All clients will then be informed of the result.
 */
public class FileOfferExchangeHandler extends ANetworkHandler<FileExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferExchangeHandler.class);

    /**
     * The id of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * A storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    public FileOfferExchangeHandler(UUID fileExchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IUser user, ClientManager clientManager, IClient client, FileOfferRequest fileOfferRequest) {
        super(user, clientManager, client, fileOfferRequest);
        this.clientDevice = clientDevice;
        this.fileExchangeId = fileExchangeId;
        this.storageAdapter = storageAdapter;
    }

    @Override
    public FileExchangeHandlerResult handleResult()
            throws SyncFailedException {

        logger.info("Starting to evaluate the other clients results");

        // we create a conflict file for our client if at least one client has another version
        boolean inConsent = true;
        for (Map.Entry<ClientDevice, IResponse> responseEntry : this.respondedClients.entrySet()) {
            if (! (responseEntry.getValue() instanceof FileOfferResponse)) {
                logger.warn("Client " + responseEntry.getKey().getClientDeviceId() + "(" + responseEntry.getKey().getPeerAddress().inetAddress().getHostAddress() + ":" + responseEntry.getKey().getPeerAddress().tcpPort() + ") did not return a FileOfferResponse but " + responseEntry.getValue().getClass().getName() + ". Therefore his result is ignored.");
                continue;
            }

            FileOfferResponse fileOfferResponse = (FileOfferResponse) responseEntry.getValue();

            if (fileOfferResponse.hasConflict()) {
                logger.info("Client " + responseEntry.getValue().getClientDevice().getClientDeviceId() + " (" + responseEntry.getValue().getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + responseEntry.getKey().getPeerAddress().tcpPort() + ") had detected a conflict.");
                inConsent = false;

                // actually, we could break here but then we would lost the log entry
                // which clients do have a conflict
            }
        }


        IEvent conflictFileEvent = null;
        Map<String, PeerAddress> conflictFiles = new HashMap<>();

        // create conflict file
        String relativeFilePath = ((FileOfferRequest) super.request).getRelativeFilePath();
        IPathElement oldPathElement = new LocalPathElement(relativeFilePath);

        if (! inConsent) {

            try {
                IFileMetaInfo fileMetaInfo = this.storageAdapter.getMetaInformation(oldPathElement);

                String newFileName = Naming.getConflictFileName(relativeFilePath, fileMetaInfo.isFile(), fileMetaInfo.getFileExtension(), this.clientDevice.getClientDeviceId().toString());
                IPathElement conflictFile = new LocalPathElement(newFileName);
                StorageType storageType = fileMetaInfo.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

                // move local path element
                this.storageAdapter.move(storageType, oldPathElement, conflictFile);

                List<Version> fileVersions = ((FileOfferRequest) super.request).getFileVersions();
                Version lastFileVersion = fileVersions.size() > 0 ? fileVersions.get(fileVersions.size() - 1) : null;
                String lastFileVersionHash = (null != lastFileVersion) ? lastFileVersion.getHash() : null;

                conflictFileEvent = new MoveEvent(
                        Paths.get(oldPathElement.getPath()),
                        Paths.get(conflictFile.getPath()),
                        relativeFilePath,
                        lastFileVersionHash,
                        System.currentTimeMillis()
                );

                // create expected conflict file names for each client which has a conflict
                for (Map.Entry<ClientDevice, IResponse> entry : this.respondedClients.entrySet()) {
                    if (! (entry.getValue() instanceof FileOfferResponse)) {
                        logger.warn("Client " + entry.getKey().getClientDeviceId() + "(" + entry.getKey().getPeerAddress().inetAddress().getHostAddress() + ":" + entry.getKey().getPeerAddress().tcpPort() + ") did not return a FileOfferResponse but " + entry.getValue().getClass().getName() + ". Therefore his result is ignored and no conflict file is generated for him.");
                        continue;
                    }

                    FileOfferResponse fileOfferResponse = (FileOfferResponse) entry.getValue();

                    if (fileOfferResponse.hasConflict() && fileOfferResponse.hasAcceptedOffer()) {
                        // assemble conflict file name for the other peers
                        String otherClientConflictFileName = Naming.getConflictFileName(
                                ((FileOfferRequest) super.request).getRelativeFilePath(),
                                fileMetaInfo.isFile(),
                                fileMetaInfo.getFileExtension(),
                                entry.getKey().getClientDeviceId().toString()
                        );

                        // get conflict file from peer with the given address
                        conflictFiles.put(otherClientConflictFileName, entry.getKey().getPeerAddress());
                    }
                }

            } catch (InputOutputException e) {
                // TODO: improve this
                logger.error("Could not create conflict file. Sending no conflict files for all clients. Will result in data loss. Message: " + e.getMessage(), e);
            }
        } else {
            // if not in conflict, we will send just the location of the offered file as result
            conflictFiles.put(((FileOfferRequest) super.request).getRelativeFilePath(), this.client.getPeerAddress());
        }

        FileOfferResultRequest fileOfferResultRequest = new FileOfferResultRequest(
                this.fileExchangeId,
                this.clientDevice,
                conflictFiles,
                ! inConsent
        );

        // Notify all clients about the result
        for (Map.Entry<ClientDevice, IResponse> responseEntry : this.respondedClients.entrySet()) {
            logger.info("Sending the negotiated result to client " + responseEntry.getKey().getPeerAddress().inetAddress().getHostAddress() + ":" + responseEntry.getKey().getPeerAddress().tcpPort());
            this.client.sendDirect(responseEntry.getKey().getPeerAddress(), fileOfferResultRequest);
        }

        // return the conflict file event
        return new FileExchangeHandlerResult(this.fileExchangeId, conflictFileEvent);
    }

}
