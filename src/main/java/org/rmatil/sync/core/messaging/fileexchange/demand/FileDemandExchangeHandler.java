package org.rmatil.sync.core.messaging.fileexchange.demand;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.exception.ConnectionFailedException;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileDemandExchangeHandler extends ANetworkHandler<FileDemandExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileDemandExchangeHandler.class);

    /**
     * The peer address from which to fetch the file
     */
    protected ClientDevice fetchAddress;

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;


    public FileDemandExchangeHandler(IStorageAdapter storageAdapter, IUser user, IClientManager clientManager, IClient client, ClientDevice fetchAddress, FileDemandRequest fileDemandRequest) {
        super(client);
        this.fetchAddress = fetchAddress;
        this.storageAdapter = storageAdapter;
    }

    public void sendRequest() {
//        logger.debug("Sending request " + this.request.getExchangeId() + " to client " + this.fetchAddress.getPeerAddress().inetAddress().getHostAddress() + ":" + this.fetchAddress.getPeerAddress().tcpPort());
//        try {
//            FutureDirect futureDirect = this.client.sendDirect(this.fetchAddress.getPeerAddress(), this.request);
//            this.notifiedClients.put(this.fetchAddress, futureDirect);
//        } catch (ObjectSendFailedException e) {
//            logger.error("Failed to send request to client " + this.fetchAddress.getClientDeviceId() + " (" + this.fetchAddress.getPeerAddress().inetAddress().getHostAddress() + ":" + this.fetchAddress.getPeerAddress().tcpPort() + "). Message: " + e.getMessage());
//        }
    }


    protected FileDemandExchangeHandlerResult handleResult()
            throws ConnectionFailedException {

//        // this is currently only one
//        for (Map.Entry<ClientDevice, IResponse> responseEntry : this.respondedClients.entrySet()) {
//            if (! (responseEntry.getValue() instanceof FileDemandResponse)) {
//                logger.warn("Client " + responseEntry.getKey().getClientDeviceId() + "(" + responseEntry.getKey().getPeerAddress().inetAddress().getHostAddress() + ":" + responseEntry.getKey().getPeerAddress().tcpPort() + ") did not return a FileDemandResponse but " + responseEntry.getValue().getClass().getName() + ". Therefore the requested file can not be fetched.");
//                continue;
//            }
//
//            FileDemandResponse fileDemandResponse = (FileDemandResponse) responseEntry.getValue();
//
//            return new FileDemandExchangeHandlerResult(
//                    ((FileDemandResponse) responseEntry.getValue()).fileExchangeId,
//                    fileDemandResponse.getChunkCounter(),
//                    fileDemandResponse.getChunkSize(),
//                    fileDemandResponse.getTotalNrOfChunks(),
//                    fileDemandResponse.getData(),
//                    fileDemandResponse.getTotalFileSize()
//            );
//        }
//
//        logger.error("Could not fetch data for request " + super.request.getExchangeId());
        return null;
    }

    @Override
    public void run() {

    }

    @Override
    public void onResponse(IResponse iResponse) {

    }

    @Override
    public FileDemandExchangeHandlerResult getResult() {
        return null;
    }
}
