package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.core.messaging.sharingexchange.shared.SharedResponse;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileDemandExchangeHandler extends ANetworkHandler<FileDemandExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileDemandExchangeHandler.class);

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    protected IClientManager clientManager;

    protected ClientLocation fetchAddress;

    protected String pathToFetch;

    protected int chunkCounter = 0;

    protected UUID exchangeId;

    public FileDemandExchangeHandler(IStorageAdapter storageAdapter, IClient client, IClientManager clientManager, ClientLocation fetchAddress, String pathToFetch, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.fetchAddress = fetchAddress;
        this.pathToFetch = pathToFetch;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {

            List<ClientLocation> receiverAddresses = new ArrayList<>();
            receiverAddresses.add(this.fetchAddress);

            ClientDevice clientDevice = new ClientDevice(
                    super.client.getUser().getUserName(),
                    super.client.getClientDeviceId(),
                    super.client.getPeerAddress()
            );


            FileDemandRequest fileDemandRequest = new FileDemandRequest(
                    this.exchangeId,
                    clientDevice,
                    this.pathToFetch,
                    receiverAddresses,
                    this.chunkCounter
            );

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

        if (this.chunkCounter == fileDemandResponse.getTotalNrOfChunks()) {
            // we received the last chunk needed
            super.onResponse(response);
            return;
        }

        this.chunkCounter++;

        this.run();
    }

    @Override
    public FileDemandExchangeHandlerResult getResult() {
        return new FileDemandExchangeHandlerResult();
    }
}
