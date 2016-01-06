package org.rmatil.sync.core.messaging.fileexchange.push;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.network.core.model.Data;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FilePushRequest implements IRequest {

    private static final Logger logger = LoggerFactory.getLogger(FilePushRequest.class);

    protected UUID exchangeId;
    protected ClientDevice clientDevice;

    protected String relativeFilePath;
    /**
     * The number of the chunk which is returned
     */
    protected long chunkCounter;

    /**
     * The total number of chunks which have
     * to been requested to get the complete file
     */
    protected long totalNrOfChunks;

    /**
     * The file size in bytes
     */
    protected long totalFileSize;

    /**
     * The actual data of the request
     */
    protected Data data;

    /**
     * The chunk size used for the whole transport of the file. In Bytes.
     */
    protected int chunkSize;

    protected List<ClientLocation> receiverAddresses;

    protected IClient client;
    protected IStorageAdapter storageAdapter;
    protected IObjectStore    objectStore;
    protected MBassador       globalEventBus;

    public FilePushRequest(UUID exchangeId, ClientDevice clientDevice, String relativeFilePath, long chunkCounter, int chunkSize, long totalNrOfChunks, long totalFileSize, Data data, ClientLocation receiverAddress) {
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.relativeFilePath = relativeFilePath;
        this.receiverAddresses = new ArrayList<>();
        this.receiverAddresses.add(receiverAddress);
        this.chunkCounter = chunkCounter;
        this.chunkSize = chunkSize;
        this.totalNrOfChunks = totalNrOfChunks;
        this.totalFileSize = totalFileSize;
        this.data = data;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return this.receiverAddresses;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    public void setGlobalEventBus(MBassador globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public UUID getExchangeId() {
        return this.exchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    @Override
    public void sendResponse(IResponse iResponse) {
        if (null == this.client) {
            throw new IllegalStateException("A client instance is required to send a response back");
        }

        this.client.sendDirect(iResponse.getReceiverAddress().getPeerAddress(), iResponse);
    }

    @Override
    public void run() {
        logger.info("Writing chunk " + this.chunkCounter + " for file " + this.relativeFilePath);

        IPathElement localPathElement = new LocalPathElement(this.relativeFilePath);

        if (this.chunkCounter > 0) {
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new ModifyEvent(
                            Paths.get(this.relativeFilePath),
                            Paths.get(this.relativeFilePath).getFileName().toString(),
                            "weIgnoreTheHash",
                            System.currentTimeMillis()
                    )
            ));
        } else {
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new CreateEvent(
                            Paths.get(this.relativeFilePath),
                            Paths.get(this.relativeFilePath).getFileName().toString(),
                            "weIgnoreTheHash",
                            System.currentTimeMillis()
                    )
            ));
        }

        try {
            this.storageAdapter.persist(StorageType.FILE, localPathElement, this.chunkCounter * this.chunkSize, this.data.getContent());
        } catch (InputOutputException e) {
            logger.error("Could not write chunk " + this.chunkCounter + " of file " + this.relativeFilePath);
        }

        if (this.chunkCounter == this.totalNrOfChunks) {
            // indicate we got all chunks
            this.chunkCounter = -1;
        }

        IResponse response = new FilePushResponse(
                this.exchangeId,
                this.clientDevice,
                this.relativeFilePath,
                new ClientLocation(this.clientDevice.getClientDeviceId(), this.clientDevice.getPeerAddress()),
                this.chunkCounter
        );

        this.sendResponse(response);
    }
}
