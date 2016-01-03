package org.rmatil.sync.core.messaging.fileexchange.push;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilePushRequestHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FilePushRequestHandler.class);

    protected ExecutorCompletionService<FileDemandExchangeHandlerResult> completionService;

    protected ClientDevice clientDevice;

    protected IStorageAdapter storageAdapter;
    protected IUser           user;
    protected IClient         client;
    protected IClientManager  clientManager;


    public FilePushRequestHandler(ClientDevice clientDevice, IStorageAdapter storageAdapter, IUser user, IClient client, IClientManager clientManager) {
        this.clientDevice = clientDevice;
        this.storageAdapter = storageAdapter;
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        this.completionService = new ExecutorCompletionService<>(executorService);
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FilePushRequest)) {
            logger.error("Received an unknown push request. Aborting...");
            return null;
        }

        FileDemandRequest fileDemandRequest = new FileDemandRequest(
                UUID.randomUUID(),
                clientDevice,
                ((FilePushRequest) request).getRelativeFilePath(),
                0
        );

        FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                this.storageAdapter,
                this.user,
                this.clientManager,
                this.client,
                ((FilePushRequest) request).getClientDevice(),
                fileDemandRequest
        );

//        this.completionService.submit(fileDemandExchangeHandler);

        return new FilePushResponse();
    }
}
