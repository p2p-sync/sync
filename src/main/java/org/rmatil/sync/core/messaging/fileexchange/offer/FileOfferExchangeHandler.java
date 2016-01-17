package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Tries to determine whether a particular {@link IEvent} resp. {@link SerializableEvent}
 * causes a conflict on any connected client.
 * This is done, by sending {@link FileOfferRequest} to all connected clients. These
 * determine whether the event causes a conflict and response with the appropriate answer.
 * If a conflict is detected, a {@link FileOfferExchangeHandlerResult} is returned having
 * the fields for conflict set to true.
 */
public class FileOfferExchangeHandler extends ANetworkHandler<FileOfferExchangeHandlerResult> implements ILocalStateResponseCallback{

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

    /**
     * The actual event to check for conflicts on other clients
     */
    protected IEvent eventToPropagate;

    /**
     * A list of clients which responded to the file offer request
     */
    protected List<IResponse> respondedClients;

    /**
     * The object store to access the file versions
     */
    protected IObjectStore objectStore;

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The global bus event used for adding events
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * @param exchangeId       The exchange id used for the file offer handling
     * @param clientDevice     The client device used to identify the sending client for any file offer requests
     * @param clientManager    The client manager to access client locations
     * @param client           The client to send messages
     * @param objectStore      The object store to get versions of a particular file
     * @param storageAdapter   The storage adapter for the synchronized folder
     * @param globalEventBus   The global event bus to add events to
     * @param eventToPropagate The actual event to check for conflicts
     */
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
                    // do not use toAbsolutePath() since we could have also paths starting with "./myDir"
                    Path relPath = this.storageAdapter.getRootDir().relativize(entry);
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
            PathObject pathObject;

            try {
                pathObject = this.objectStore.getObjectManager().getObjectForPath(pathToCheck);
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
    public List<String> getAffectedFilePaths() {
        List<String> affectedFiles = new ArrayList<>();
        affectedFiles.add(this.eventToPropagate.getPath().toString());
        return affectedFiles;
    }

    @Override
    public void onResponse(IResponse iResponse) {
        logger.info("Received response for exchange " + iResponse.getExchangeId() + " of client " + iResponse.getClientDevice().getClientDeviceId() + " (" + iResponse.getClientDevice().getPeerAddress().inetAddress().getHostAddress() + ":" + iResponse.getClientDevice().getPeerAddress().tcpPort() + ")");

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

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
