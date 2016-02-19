package org.rmatil.sync.core.messaging.fileexchange.demand;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.CleanModifyIgnoreEventsBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An exchange handler to request missing files from another client.
 */
public class FileDemandExchangeHandler extends ANetworkHandler<FileDemandExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileDemandExchangeHandler.class);

    /**
     * Wait a maximum of 2 minutes for a file exchange to complete
     */
    protected static final long MAX_FILE_WAITNG_TIME = 120000L;

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The client manager to fetch client locations
     */
    protected INodeManager nodeManager;

    /**
     * The actual client address from which to get the missing file
     */
    protected NodeLocation fetchAddress;

    /**
     * The relative path (rel. to the synced-folder root)
     * of the file to fetch
     */
    protected String pathToFetch;

    /**
     * The counter for chunks, indicating which
     * chunks have been transmitted already
     */
    protected int chunkCounter = 0;

    /**
     * The id of the demand exchange
     */
    protected UUID exchangeId;

    /**
     * The countdown latch used for indicating that all
     * chunks have been received.
     * Using the parent's latch will not work, since its
     * reference its always overwritten on re-running run()...
     */
    protected CountDownLatch receivedAllChunksCountDownLatch;

    /**
     * The global event bus to publish messages to
     */
    protected MBassador<IBusEvent> globalEventBus;

    /**
     * @param storageAdapter The storage adapter to access the synced folder
     * @param client         The client to send messages
     * @param nodeManager    The client manager to fetch other clients' locations
     * @param fetchAddress   The address from the client from which the file should be fetched
     * @param pathToFetch    The path to the file which is requested
     * @param exchangeId     The id of the exchange
     */
    public FileDemandExchangeHandler(IStorageAdapter storageAdapter, INode client, INodeManager nodeManager, MBassador<IBusEvent> globalEventBus, NodeLocation fetchAddress, String pathToFetch, UUID exchangeId) {
        super(client);
        this.nodeManager = nodeManager;
        this.storageAdapter = storageAdapter;
        this.globalEventBus = globalEventBus;
        this.fetchAddress = fetchAddress;
        this.pathToFetch = pathToFetch;
        this.exchangeId = exchangeId;
        this.receivedAllChunksCountDownLatch = new CountDownLatch(1);
    }

    @Override
    public void run() {
        try {
            List<NodeLocation> receiverAddresses = new ArrayList<>();
            receiverAddresses.add(this.fetchAddress);

            ClientDevice clientDevice = new ClientDevice(
                    super.node.getUser().getUserName(),
                    super.node.getClientDeviceId(),
                    super.node.getPeerAddress()
            );


            FileDemandRequest fileDemandRequest = new FileDemandRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    clientDevice,
                    this.pathToFetch,
                    receiverAddresses,
                    this.chunkCounter
            );

            // clear notified clients, otherwise the countdown latch will be
            // increase by one each time we send a request...
            super.notifiedClients.clear();
            super.sendRequest(fileDemandRequest);

        } catch (Exception e) {
            logger.error("Got exception in FileDemandExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse response) {
        if (! (response instanceof FileDemandResponse)) {
            logger.error("Expected response to be instance of " + FileDemandResponse.class.getName() + " but got " + response.getClass().getName());
            return;
        }

        FileDemandResponse fileDemandResponse = (FileDemandResponse) response;

        logger.info("Writing chunk " + fileDemandResponse.getChunkCounter() + " for file " + fileDemandResponse.getRelativeFilePath() + " for exchangeId " + fileDemandResponse.getExchangeId());

        IPathElement localPathElement = new LocalPathElement(fileDemandResponse.getRelativeFilePath());

        if (StatusCode.DENIED.equals(fileDemandResponse.getStatusCode()) || StatusCode.FILE_MISSING.equals(fileDemandResponse.getStatusCode())) {
            // the other client does not have the file anymore or we do not have the correct access rights to fetch it...
            logger.error("The answering client (" + fileDemandResponse.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + fileDemandResponse.getClientDevice().getPeerAddress().tcpPort() + ") does not have the requested file (anymore) or denied our request due to missing access rights or due to another exchange for the file. Aborting file demand " + this.exchangeId);
            super.onResponse(fileDemandResponse);
            this.receivedAllChunksCountDownLatch.countDown();
            return;
        }

        // TODO: check whether the file isDeleted on each write, there might be a concurrent incoming delete request
        // -> affected FilePaths? in ObjectDataReply?

        this.publishIgnoreEvents(fileDemandResponse, localPathElement);

        if (StatusCode.ACCEPTED.equals(fileDemandResponse.getStatusCode())) {
            if (fileDemandResponse.isFile()) {
                try {
                    this.storageAdapter.persist(StorageType.FILE, localPathElement, fileDemandResponse.getChunkCounter() * fileDemandResponse.getChunkSize(), fileDemandResponse.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + fileDemandResponse.getChunkCounter() + " of file " + fileDemandResponse.getRelativeFilePath() + ". Message: " + e.getMessage(), e);
                }
            } else {
                try {
                    if (! this.storageAdapter.exists(StorageType.DIRECTORY, localPathElement)) {
                        this.storageAdapter.persist(StorageType.DIRECTORY, localPathElement, null);
                    }
                } catch (InputOutputException e) {
                    logger.error("Could not create directory " + localPathElement.getPath() + ". Message: " + e.getMessage());
                }
            }
        }

        if (StatusCode.ACCEPTED.equals(fileDemandResponse.getStatusCode()) &&
                this.chunkCounter == fileDemandResponse.getTotalNrOfChunks()) {
            // we received the last chunk needed

            // now check that we got the same checksum for the file
            try {
                String checksum = "";

                // only files may have a checksum
                if (fileDemandResponse.isFile()) {
                    checksum = this.storageAdapter.getChecksum(localPathElement);
                }

                if (null == fileDemandResponse.getChecksum() || fileDemandResponse.getChecksum().equals(checksum)) {
                    // checksums match or the other side failed to compute one
                    logger.info("Checksums match (" + fileDemandResponse.getChecksum() + " = " + checksum + "). Stopping FileDemand " + this.exchangeId);
                    // clean all modify ignore events
                    this.globalEventBus.publish(new CleanModifyIgnoreEventsBusEvent(
                            localPathElement.getPath()
                    ));
                    super.onResponse(response);
                    this.receivedAllChunksCountDownLatch.countDown();
                    return;
                } else {
                    logger.info("Checksums did not match. Restarting FileDemand at chunk 0 for exchange" + this.exchangeId);
                    // restart to fetch the whole file
                    this.chunkCounter = - 1; // -1 since the chunk counter is just increased on the end of this method
                    // delete all file contents fetched until now
                    if (fileDemandResponse.isFile()) {
                        this.storageAdapter.persist(StorageType.FILE, localPathElement, new byte[0]);
                    } else {
                        this.storageAdapter.persist(StorageType.DIRECTORY, localPathElement, null);
                    }
                }
            } catch (InputOutputException e) {
                logger.error("Failed to generate the checksum for file " + localPathElement.getPath() + " on exchange " + this.exchangeId + ". Accepting the file. Message: " + e.getMessage());
                super.onResponse(response);
                this.receivedAllChunksCountDownLatch.countDown();
                return;
            }
        } else if (StatusCode.FILE_CHANGED.equals(fileDemandResponse.getStatusCode())) {
            // the file changed while we are transferring it (i.e. it is shorter than before)
            this.chunkCounter = - 1;
            // delete all file contents fetched until now
            try {
                if (fileDemandResponse.isFile()) {
                    this.storageAdapter.persist(StorageType.FILE, localPathElement, new byte[0]);
                } else {
                    this.storageAdapter.persist(StorageType.DIRECTORY, localPathElement, null);
                }
            } catch (InputOutputException e) {
                logger.error("Failed to clear the file again");
            }
        }

        this.chunkCounter++;

        this.run();
    }

    @Override
    public void await()
            throws InterruptedException {
        // do not await in super, since its countdown latch will be
        // rewritten each time we call run()
        this.receivedAllChunksCountDownLatch.await(MAX_FILE_WAITNG_TIME, TimeUnit.MILLISECONDS);
    }

    @Override
    public void await(long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        // do not await in super, since its countdown latch will be
        // rewritten each time we call run()
        this.receivedAllChunksCountDownLatch.await(timeout, timeUnit);
    }

    @Override
    public boolean isCompleted() {
        // do not await in super, since its countdown latch will be
        // rewritten each time we call run()
        // -> therefore we have to await here
        return null != this.receivedAllChunksCountDownLatch && 0L == this.receivedAllChunksCountDownLatch.getCount();
    }

    @Override
    public FileDemandExchangeHandlerResult getResult() {
        return new FileDemandExchangeHandlerResult();
    }

    protected void publishIgnoreEvents(FileDemandResponse fileDemandResponse, IPathElement localPathElement) {
        // if the chunk counter is greater than 0
        // we only modify the existing file, so we generate an ignore modify event
        if (fileDemandResponse.getChunkCounter() > 0) {
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new ModifyEvent(
                            Paths.get(fileDemandResponse.getRelativeFilePath()),
                            Paths.get(fileDemandResponse.getRelativeFilePath()).getFileName().toString(),
                            "weIgnoreTheHash",
                            System.currentTimeMillis()
                    )
            ));
        } else if (fileDemandResponse.getChunkCounter() > - 1) {
            // we check for local existence, if the file already exists, we just ignore the
            // modify event, otherwise we ignore the create event
            try {
                if (this.storageAdapter.exists(StorageType.FILE, localPathElement) || this.storageAdapter.exists(StorageType.DIRECTORY, localPathElement)) {
                    this.globalEventBus.publish(new IgnoreBusEvent(
                            new ModifyEvent(
                                    Paths.get(fileDemandResponse.getRelativeFilePath()),
                                    Paths.get(fileDemandResponse.getRelativeFilePath()).getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));
                } else {
                    this.globalEventBus.publish(new IgnoreBusEvent(
                            new CreateEvent(
                                    Paths.get(fileDemandResponse.getRelativeFilePath()),
                                    Paths.get(fileDemandResponse.getRelativeFilePath()).getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));

                    this.globalEventBus.publish(new AddSharerToObjectStoreBusEvent(
                            fileDemandResponse.getRelativeFilePath(),
                            fileDemandResponse.getSharers()
                    ));
                }
            } catch (InputOutputException e) {
                logger.error("Can not determine whether the file " + localPathElement.getPath() + " exists. Message: " + e.getMessage() + ". Just checking the chunk counters...");
            }
        }
    }
}
