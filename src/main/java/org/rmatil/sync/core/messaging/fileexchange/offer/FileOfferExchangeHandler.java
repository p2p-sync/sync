package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Implements the crucial step of deciding whether a conflict
 * file has to be created locally or not.
 * <p>
 * All clients will then be informed of the result.
 */
public class FileOfferExchangeHandler extends ANetworkHandler<FileOfferExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferExchangeHandler.class);

    /**
     * The id of the file exchange
     */
    protected UUID exchangeId;

    /**
     * A storage adapter to access the synchronized folder
     */
    protected IClientManager clientManager;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    protected IEvent eventToPropagate;

    protected List<IResponse> respondedClients;

    protected IObjectStore objectStore;

    protected IStorageAdapter storageAdapter;

    protected MBassador<IBusEvent> globalEventBus;

    public FileOfferExchangeHandler(UUID exchangeId, ClientDevice clientDevice, IClientManager clientManager, IClient client, IObjectStore objectStore, IStorageAdapter storageAdapter, MBassador<IBusEvent> globalEventBus, IEvent eventToPropagate) {
        super(client);
        this.clientDevice = clientDevice;
        this.exchangeId = exchangeId;
        this.clientManager = clientManager;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;
        this.globalEventBus = globalEventBus;
        this.eventToPropagate = eventToPropagate;
        this.respondedClients = new ArrayList<>();
    }

    @Override
    public void run() {
        String pathToCheck = this.eventToPropagate.getEventName().equals(MoveEvent.EVENT_NAME) ? ((MoveEvent) this.eventToPropagate).getNewPath().toString() : this.eventToPropagate.getPath().toString();

        boolean isDir = false;
        try {
            isDir = this.storageAdapter.isDir(new LocalPathElement(pathToCheck));
        } catch (InputOutputException e) {
            logger.error("Could not check whether the file " + pathToCheck + " is a file or directory");
        }

        // since this sync is triggered by a move, the actual operation is already
        // done on this client, therefore we traverse the dir on the new path
        if (isDir && this.eventToPropagate instanceof MoveEvent) {
            // we only offer the move event from the "root" directory
            Path dirToMove = this.storageAdapter.getRootDir().resolve(((MoveEvent) this.eventToPropagate).getNewPath());
            try (Stream<Path> paths = Files.walk(dirToMove)) {
                paths.forEach((entry) -> {
                    Path relPath = this.storageAdapter.getRootDir().toAbsolutePath().relativize(entry);
                    Path oldPath = this.eventToPropagate.getPath().resolve(((MoveEvent) this.eventToPropagate).getNewPath().relativize(relPath));

                    globalEventBus.publish(new IgnoreBusEvent(
                            new MoveEvent(
                                    oldPath,
                                    relPath,
                                    entry.getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));
                });
            } catch (IOException e) {
                logger.error("Could not create ignore events for moving " + this.eventToPropagate.getPath().toString() + " to " + ((MoveEvent) this.eventToPropagate).getNewPath().toString() + ". Message: " + e.getMessage());
            }
        }

        // Fetch client locations from the DHT
        List<ClientLocation> clientLocations;
        try {
            clientLocations = this.clientManager.getClientLocations(super.client.getUser());
        } catch (InputOutputException e) {
            logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
            return;
        }

        Version versionBefore = null;
        // we check versions only for files
        if (! isDir) {
            Map<String, String> indexPaths = this.objectStore.getObjectManager().getIndex().getPaths();
            String hash = indexPaths.get(pathToCheck);

            PathObject pathObject;
            try {
                pathObject = this.objectStore.getObjectManager().getObject(hash);
            } catch (InputOutputException e) {
                logger.error("Can not read versions from object store. Message: " + e.getMessage());
                return;
            }

            // get version before the one we got from the event to propagate
            for (Version entry : pathObject.getVersions()) {
                if (entry.getHash().equals(this.eventToPropagate.getHash())) {
                    // versionBefore contains now the version before this element
                    break;
                }

                versionBefore = entry;
            }
        }

        IRequest request = new FileOfferRequest(
                this.exchangeId,
                this.clientDevice,
                SerializableEvent.fromEvent(this.eventToPropagate, (null != versionBefore) ? versionBefore.getHash() : null, ! isDir),
                clientLocations
        );

        super.sendRequest(request);
    }

    @Override
    public void onResponse(IResponse iResponse) {
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");
        this.respondedClients.add(iResponse);
        super.countDownLatch.countDown();
    }

    @Override
    public FileOfferExchangeHandlerResult getResult() {
        boolean hasConflict = false;
        boolean hasAccepted = true;

        for (IResponse response : this.respondedClients) {
            if (! (response instanceof FileOfferResponse)) {
                logger.warn("Received unknown response from client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")");
                continue;
            }

            if (! ((FileOfferResponse) response).hasAcceptedOffer()) {
                logger.info("Client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")" + " denied offer " + response.getExchangeId());
                hasAccepted = false;
            }

            if (((FileOfferResponse) response).hasConflict()) {
                logger.info("Client " + response.getClientDevice().getClientDeviceId() + " (" + response.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ")" + " detected conflict for offer " + response.getExchangeId());
                hasConflict = true;
            }
        }

        return new FileOfferExchangeHandlerResult(hasAccepted, hasConflict);
    }
}
