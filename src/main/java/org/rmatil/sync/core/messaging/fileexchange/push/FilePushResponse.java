package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import javax.sound.midi.Receiver;
import java.util.UUID;

public class FilePushResponse implements IResponse {

    /**
     * The relative path to the file which should be returned
     */
    protected String relativeFilePath;

    /**
     * The counter which indicates which chunk should be requested
     */
    protected long chunkCounter;

    /**
     * The client device which is sending this request
     */
    protected ClientDevice clientDevice;

    /**
     * The identifier of the file exchange
     */
    protected UUID exchangeId;

    protected ClientLocation receiverAddress;

    /**
     * @param exchangeId   The identifier of the file exchange
     * @param clientDevice     The client device which is requesting the file demand (i.e. this client)
     * @param relativeFilePath The relative path to the file which should be returned
     * @param chunkCounter     The chunk number which should returned in the corresponding response to this request
     */
    public FilePushResponse(UUID exchangeId, ClientDevice clientDevice, String relativeFilePath, ClientLocation receiverAddress, long chunkCounter) {
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.relativeFilePath = relativeFilePath;
        this.chunkCounter = chunkCounter;
        this.receiverAddress = receiverAddress;
    }

    /**
     * Returns the relative path to the file which should be returned
     *
     * @return The relative path
     */
    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    /**
     * Returns the chunk index of the chunk which should be returned
     *
     * @return The chunk number to return
     */
    public long getChunkCounter() {
        return chunkCounter;
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
    public ClientLocation getReceiverAddress() {
        return this.receiverAddress;
    }
}
