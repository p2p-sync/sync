package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequest;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.ClientManager;
import org.rmatil.sync.network.core.exception.ConnectionFailedException;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class FilePushExchangeHandler extends ANetworkHandler<FilePushExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferExchangeHandler.class);

    /**
     * The id of the file exchange
     */
    protected UUID fileExchangeId;

    /**
     * A storage adapter to access the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    /**
     * The client device information
     */
    protected ClientDevice clientDevice;

    public FilePushExchangeHandler(UUID fileExchangeId, ClientDevice clientDevice, IStorageAdapter storageAdapter, IUser user, ClientManager clientManager, IClient client, FilePushRequest filePushRequest) {
        super(client);
        this.clientDevice = clientDevice;
        this.fileExchangeId = fileExchangeId;
        this.storageAdapter = storageAdapter;
    }

    protected FilePushExchangeHandlerResult handleResult()
            throws ConnectionFailedException {

        // TODO: we do not really care what the clients do with the push request...

        return new FilePushExchangeHandlerResult();
    }

    @Override
    public void run() {

    }

    @Override
    public void onResponse(IResponse iResponse) {

    }

    @Override
    public FilePushExchangeHandlerResult getResult() {
        return null;
    }
}
