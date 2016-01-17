package org.rmatil.sync.core.syncer.background.masterelection;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MasterElectionExchangeHandler extends ANetworkHandler<MasterElectionExchangeHandlerResult> {

    private static final Logger logger = LoggerFactory.getLogger(MasterElectionExchangeHandler.class);

    protected IClientManager clientManager;

    protected UUID exchangeId;

    protected List<MasterElectionResponse> electionResponses = new ArrayList<>();

    public MasterElectionExchangeHandler(IClient client, IClientManager clientManager, UUID exchangeId) {
        super(client);
        this.clientManager = clientManager;
        this.exchangeId = exchangeId;
    }

    @Override
    public void run() {
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
            // we are the master
            super.countDownLatch = new CountDownLatch(0);

            // "fake" a response
            this.electionResponses.add(new MasterElectionResponse(
                    exchangeId,
                    new ClientDevice(super.client.getUser().getUserName(),
                            super.client.getClientDeviceId(),
                            super.client.getPeerAddress()
                    ),
                    electedMaster,
                    true
            ));

            super.waitForSentCountDownLatch.countDown();

            return;
        }

        // send the election request to the master
        ArrayList<ClientLocation> receivers = new ArrayList<>();
        receivers.add(electedMaster);
        MasterElectionRequest masterElectionRequest = new MasterElectionRequest(
                this.exchangeId,
                new ClientDevice(
                        super.client.getUser().getUserName(),
                        super.client.getClientDeviceId(),
                        super.client.getPeerAddress()
                ),
                receivers,
                System.currentTimeMillis()
        );

        super.sendRequest(masterElectionRequest);
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

        // TODO: we expect only one client to reply

        // get all positive responses
        for (MasterElectionResponse response : this.electionResponses) {
            if (response.hasAccepted) {
                positiveResponses.add(response);
            }
        }

        // sort ascending
        positiveResponses.sort((o1, o2) -> o1.getClientDevice().getPeerAddress().peerId().compareTo(o2.getClientDevice().getPeerAddress().peerId()));

        // use highest peer id as master
        MasterElectionResponse electedMaster = positiveResponses.get(Math.max(0, positiveResponses.size() - 1));

        if (null != electedMaster) {
            return new MasterElectionExchangeHandlerResult(electedMaster.getClientDevice());
        }

        // if no other client accepted the request, we elect ourself as master
        return new MasterElectionExchangeHandlerResult(
                new ClientDevice(
                        super.client.getUser().getUserName(),
                        super.client.getClientDeviceId(),
                        super.client.getPeerAddress()
                )
        );
    }
}
