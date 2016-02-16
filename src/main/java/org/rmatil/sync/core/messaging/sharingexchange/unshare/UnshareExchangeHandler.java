package org.rmatil.sync.core.messaging.sharingexchange.unshare;

import org.rmatil.sync.core.messaging.StatusCode;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.NodeLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class UnshareExchangeHandler extends ANetworkHandler<UnshareExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(UnshareExchangeHandler.class);

    protected NodeLocation receiverAddress;

    protected UUID fileId;

    protected UUID exchangeId;

    public UnshareExchangeHandler(INode client, NodeLocation receiverAddress, UUID fileId, UUID exchangeId) {
        super(client);
        this.receiverAddress = receiverAddress;
        this.fileId = fileId;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            logger.info("Sending unshare request to client " + receiverAddress.getPeerAddress().inetAddress().getHostName() + ":" + receiverAddress.getPeerAddress().tcpPort());

            List<NodeLocation> receivers = new ArrayList<>();
            receivers.add(receiverAddress);

            UnshareRequest unshareRequest = new UnshareRequest(
                    this.exchangeId,
                    StatusCode.NONE,
                    new ClientDevice(
                            super.node.getUser().getUserName(),
                            super.node.getClientDeviceId(),
                            super.node.getPeerAddress()
                    ),
                    receivers,
                    this.fileId
            );

            super.sendRequest(unshareRequest);

        } catch (Exception e) {
            logger.error("Got exception in UnshareExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public UnshareExchangeHandlerResult getResult() {
        return new UnshareExchangeHandlerResult();
    }
}
