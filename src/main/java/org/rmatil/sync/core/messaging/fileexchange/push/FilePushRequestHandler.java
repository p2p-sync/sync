package org.rmatil.sync.core.messaging.fileexchange.push;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.AddSharerToObjectStoreBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class FilePushRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FilePushRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected FilePushRequest      request;
    protected MBassador<IBusEvent> globalEventBus;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof FilePushRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FilePushRequest.class.getName());
        }

        this.request = (FilePushRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Writing chunk " + this.request.getChunkCounter() + " for file " + this.request.getRelativeFilePath() + " for exchangeId " + this.request.getExchangeId());

            IPathElement localPathElement = new LocalPathElement(this.request.getRelativeFilePath());

            // if the chunk counter is greater than 0
            // we only modify the existing file, so we generate an ignore modify event
            if (this.request.getChunkCounter() > 0) {
                this.globalEventBus.publish(new IgnoreBusEvent(
                        new ModifyEvent(
                                Paths.get(this.request.getRelativeFilePath()),
                                Paths.get(this.request.getRelativeFilePath()).getFileName().toString(),
                                "weIgnoreTheHash",
                                System.currentTimeMillis()
                        )
                ));
            } else {
                // we check for local existence, if the file already exists, we just ignore the
                // modify event, otherwise we ignore the create event
                try {
                    if (this.storageAdapter.exists(StorageType.FILE, localPathElement) || this.storageAdapter.exists(StorageType.DIRECTORY, localPathElement)) {
                        this.globalEventBus.publish(new IgnoreBusEvent(
                                new ModifyEvent(
                                        Paths.get(this.request.getRelativeFilePath()),
                                        Paths.get(this.request.getRelativeFilePath()).getFileName().toString(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        ));
                    } else {
                        this.globalEventBus.publish(new IgnoreBusEvent(
                                new CreateEvent(
                                        Paths.get(this.request.getRelativeFilePath()),
                                        Paths.get(this.request.getRelativeFilePath()).getFileName().toString(),
                                        "weIgnoreTheHash",
                                        System.currentTimeMillis()
                                )
                        ));

                        this.globalEventBus.publish(new AddSharerToObjectStoreBusEvent(
                                this.request.getRelativeFilePath(),
                                this.request.getSharers()
                        ));
                    }
                } catch (InputOutputException e) {
                    logger.error("Can not determine whether the file " + localPathElement.getPath() + " exists. Message: " + e.getMessage() + ". Just checking the chunk counters...");
                }
            }

            if (this.request.isFile()) {
                try {
                    this.storageAdapter.persist(StorageType.FILE, localPathElement, this.request.getChunkCounter() * this.request.getChunkSize(), this.request.getData().getContent());
                } catch (InputOutputException e) {
                    logger.error("Could not write chunk " + this.request.getChunkCounter() + " of file " + this.request.getRelativeFilePath() + ". Message: " + e.getMessage(), e);
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


            long requestingChunk = this.request.getChunkCounter();
            if (this.request.getChunkCounter() == this.request.getTotalNrOfChunks()) {
                // indicate we got all chunks
                requestingChunk = - 1;
            } else {
                requestingChunk++;
            }

            IResponse response = new FilePushResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    this.request.getRelativeFilePath(),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    requestingChunk
            );

            this.sendResponse(response);
        } catch (Exception e) {
            logger.error("Error in FilePushRequestHandler for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the given response back to the client
     *
     * @param iResponse The response to send back
     */
    public void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }
}
