package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.core.syncer.background.BlockingBackgroundSyncer;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.ANetworkHandler;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Elects a master client by fetching all clients currently
 * online and using the highest peer id as master. The master
 * client is then contacted. Theoretically, master can deny such a request
 * which causes another client, which has accepted the request, to be the master.
 * If no client accepts the request, then the client having started this exchange
 * will become the master client.
 *
 * @see BlockingBackgroundSyncer
 */
public class MasterElectionExchangeHandler extends ANetworkHandler<MasterElectionExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(MasterElectionExchangeHandler.class);

    /**
     * The client manager to get all locations from
     */
    protected IClientManager clientManager;

    /**
     * The exchange id for the master election
     */
    protected UUID exchangeId;

    /**
     * A list of all responded client's responses
     */
    protected List<MasterElectionResponse> electionResponses = new ArrayList<>();

    /**
     * @param client        The client to send messages with
     * @param clientManager The client manager to fetch client locations from
     * @param exchangeId    The exchange id for the master election
     */
    public MasterElectionExchangeHandler(IClient client, IClientManager clientManager, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
        try {
            logger.info("Starting to find the master client");

            List<ClientLocation> clientLocations;
            try {
                clientLocations = this.clientManager.getClientLocations(super.client.getUser());
            } catch (InputOutputException e) {
                logger.error("Could not fetch client locations from user " + super.client.getUser().getUserName() + ". Message: " + e.getMessage());
                return;
            }

            // select highest client id
            clientLocations.sort((o1, o2) -> o1.getPeerAddress().peerId().compareTo(o2.getPeerAddress().peerId()));
            ClientLocation electedMaster = clientLocations.get(Math.max(0, clientLocations.size() - 1));

            if (electedMaster.getPeerAddress().equals(this.client.getPeerAddress())) {
                logger.info("Detected that i have the highest peer id. Electing me as master (" + this.client.getPeerAddress().inetAddress().getHostName() + ":" + this.client.getPeerAddress().tcpPort() + ") without contacting other clients");
                // we are the master

                boolean hasAccepted = true;
                if (this.client.getObjectDataReplyHandler().isMasterElected()) {
                    logger.info("I am still working on a background task. Aborting this one...");
                    hasAccepted = false;
                }

                // "fake" a response
                this.electionResponses.add(new MasterElectionResponse(
                        exchangeId,
                        new ClientDevice(super.client.getUser().getUserName(),
                                super.client.getClientDeviceId(),
                                super.client.getPeerAddress()
                        ),
                        electedMaster, // that's me
                        hasAccepted
                ));

                // still send messages to other clients to check whether
                // they are working as master on something
            }

            // send the election request to all clients
            MasterElectionRequest masterElectionRequest = new MasterElectionRequest(
                    this.exchangeId,
                    new ClientDevice(
                            super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    clientLocations,
                    System.currentTimeMillis()
            );

            super.sendRequest(masterElectionRequest);
        } catch (Exception e) {
            logger.error("Got exception in MasterElectionExchangeHandler. Message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onResponse(IResponse iResponse) {
        this.electionResponses.add((MasterElectionResponse) iResponse);

        try {
            super.waitForSentCountDownLatch.await(MAX_WAITING_TIME, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting that all requests have been sent to all clients");
        }

        super.countDownLatch.countDown();
    }

    @Override
    public MasterElectionExchangeHandlerResult getResult() {
        List<MasterElectionResponse> positiveResponses = new ArrayList<>();

        // get all positive responses
        for (MasterElectionResponse response : this.electionResponses) {
            if (response.hasAccepted()) {
                positiveResponses.add(response);
            } else {
                logger.info("Detected that a master is still working");
                return new MasterElectionExchangeHandlerResult(
                        null,
                        true,
                        response.getClientDevice()
                );
            }
        }

        // sort ascending
        positiveResponses.sort((o1, o2) -> o1.getClientDevice().getPeerAddress().peerId().compareTo(o2.getClientDevice().getPeerAddress().peerId()));

        // use highest peer id as master
        MasterElectionResponse electedMaster = positiveResponses.get(Math.max(0, positiveResponses.size() - 1));

        if (null != electedMaster) {
            return new MasterElectionExchangeHandlerResult(
                    electedMaster.getClientDevice(),
                    false,
                    null
            );
        }

        // if no other client accepted the request, we elect ourself as master
        return new MasterElectionExchangeHandlerResult(
                new ClientDevice(
                        super.client.getUser().getUserName(),
                        super.client.getClientDeviceId(),
                        super.client.getPeerAddress()
                ),
                false,
                null
        );
    }
}
