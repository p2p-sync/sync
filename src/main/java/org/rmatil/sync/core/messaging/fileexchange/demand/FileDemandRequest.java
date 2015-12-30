package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.model.ClientDevice;

import java.util.UUID;

/**
 * A request demanding particular parts of a file
 */
public class FileDemandRequest implements IRequest {

    /**
     * The requesting user
     */
    protected IUser user;

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
     * @param user             The requesting user
     * @param relativeFilePath The relative path to the file which should be returned
     * @param chunkCounter     The chunk number which should returned in the corresponding response to this request
     */
    public FileDemandRequest(UUID fileExchangeId, ClientDevice clientDevice, IUser user, String relativeFilePath, int chunkCounter) {
        this.fileExchangeId = fileExchangeId;
        this.clientDevice = clientDevice;
        this.user = user;
        this.relativeFilePath = relativeFilePath;
        this.chunkCounter = chunkCounter;
    }

    /**
     * Returns the requesting user
     *
     * @return The requesting user
     */
    public IUser getUser() {
        return user;
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
    public UUID getExchangeId() {
        return this.fileExchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }
}
