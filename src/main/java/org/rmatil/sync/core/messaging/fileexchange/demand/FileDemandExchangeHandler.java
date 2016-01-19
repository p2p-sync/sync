package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateResponseCallback;
import org.rmatil.sync.core.messaging.fileexchange.push.FilePushResponse;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.exception.ConnectionFailedException;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
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
import java.util.concurrent.TimeUnit;

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
    public void onResponse(IResponse iResponse) {
        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        FileDemandResponse fileDemandResponse = (FileDemandResponse) iResponse;

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
            super.countDownLatch.countDown();
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
