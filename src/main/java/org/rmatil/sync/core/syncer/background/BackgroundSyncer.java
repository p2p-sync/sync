package org.rmatil.sync.core.syncer.background;

import org.rmatil.sync.core.syncer.background.initsync.InitSyncExchangeHandler;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionExchangeHandler;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionExchangeHandlerResult;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class BackgroundSyncer implements IBackgroundSyncer {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundSyncer.class);

    protected IEventAggregator eventAggregator;

    protected IClient client;

    protected IClientManager clientManager;

    public BackgroundSyncer(IEventAggregator eventAggregator, IClient client, IClientManager clientManager) {
        this.eventAggregator = eventAggregator;
        this.client = client;
        this.clientManager = clientManager;
    }


    @Override
    public void run() {
        try {
            this.eventAggregator.stop();

            // TODO: check if any master election is already in progress


            UUID exchangeId = UUID.randomUUID();
            MasterElectionExchangeHandler masterElectionExchangeHandler = new MasterElectionExchangeHandler(this.client, this.clientManager, exchangeId);

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, masterElectionExchangeHandler);

            Thread masterElectorThread = new Thread(masterElectionExchangeHandler);
            masterElectorThread.setName("MasterElectionExchangeHandler-" + exchangeId);
            masterElectorThread.start();

            try {
                masterElectionExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for master elector to get all responses. Message: " + e.getMessage());
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

            if (! masterElectionExchangeHandler.isCompleted()) {
                logger.error("MasterElectionExchangeHandler should be completed after await. Aborting background syncer");
                return;
            }

            MasterElectionExchangeHandlerResult electionResult = masterElectionExchangeHandler.getResult();

            logger.info("Elected client " + electionResult.getElectedMaster().getPeerAddress().inetAddress().getHostName() + ":" + electionResult.getElectedMaster().getPeerAddress().tcpPort() + " as master");
            logger.info("Stopping event aggregators on other clients");

            // send elected master to all clients
            InitSyncExchangeHandler initSyncExchangeHandler = new InitSyncExchangeHandler(
                    this.client,
                    this.clientManager,
                    exchangeId,
                    electionResult.getElectedMaster()
            );

            Thread initSyncThread = new Thread(initSyncExchangeHandler);
            initSyncThread.setName("InitSyncExchangeHandler-" + exchangeId);
            initSyncThread.start();

            // await for init sync to complete
            initSyncExchangeHandler.await();

            if (! initSyncExchangeHandler.isCompleted()) {
                logger.error("Init sync should be completed after awaiting. Aborting init sync process");
                return;
            }



            // TODO: wait for sync to complete on other side
            // maybe by implementing a request listener interface -> restart event aggregation

            // new handler, waiting for requests (SyncPingRequest)
            // if ping is lost after N seconds, restart election


            this.eventAggregator.start();
        } catch (Exception e) {
            logger.error("Got error in BackgroundSyncer. Message: " + e.getMessage(), e);
        }
    }
}
