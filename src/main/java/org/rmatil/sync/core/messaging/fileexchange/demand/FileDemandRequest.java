package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.List;
import java.util.UUID;

/**
 * A request demanding particular parts of a file
 */
public class FileDemandRequest implements IRequest {

    /**
     * The relative path to the file which should be returned
     */
    protected String relativeFilePath;

    /**
     * The counter which indicates which chunk should be requested
     */
    protected int chunkCounter;

    /**
     * The client device which is sending this request
     */
    protected ClientDevice clientDevice;

    /**
     * The identifier of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * @param fileExchangeId   The identifier of the file exchange
     * @param clientDevice     The client device which is requesting the file demand (i.e. this client)
     * @param relativeFilePath The relative path to the file which should be returned
     * @param chunkCounter     The chunk number which should returned in the corresponding response to this request
     */
    public FileDemandRequest(UUID fileExchangeId, ClientDevice clientDevice,String relativeFilePath, int chunkCounter) {
        this.fileExchangeId = fileExchangeId;
        this.clientDevice = clientDevice;
        this.relativeFilePath = relativeFilePath;
        this.chunkCounter = chunkCounter;
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
    public int getChunkCounter() {
        return chunkCounter;
    }


    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return null;
    }

    @Override
    public void setClient(IClient iClient) {

    }

    @Override
    public UUID getExchangeId() {
        return this.fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    @Override
    public void sendResponse(IResponse iResponse) {

    }

    @Override
    public void run() {

    }
}
