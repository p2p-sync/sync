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

/**
 * {@inheritDoc}
 */
public class BlockingBackgroundSyncer implements IBackgroundSyncer {

    private static final Logger logger = LoggerFactory.getLogger(BlockingBackgroundSyncer.class);

    /**
     * The event aggregator to stop while reconciling
     */
    protected IEventAggregator eventAggregator;

    /**
     * The client to use for sending requests
     */
    protected IClient client;

    /**
     * The client manager to fetch all client locations
     */
    protected IClientManager clientManager;

    /**
     * @param eventAggregator The event aggregator to stop while reconciling
     * @param client          The client to use for sending requests
     * @param clientManager   The client manager to fetch all client locations
     */
    public BlockingBackgroundSyncer(IEventAggregator eventAggregator, IClient client, IClientManager clientManager) {
        this.eventAggregator = eventAggregator;
        this.client = client;
        this.clientManager = clientManager;
    }


    @Override
    public void run() {
        try {
            logger.info("Starting BlockingBackgroundSyncer");

            // TODO: check if any master election is already in progress

            UUID exchangeId = UUID.randomUUID();
            MasterElectionExchangeHandler masterElectionExchangeHandler = new MasterElectionExchangeHandler(this.client, this.clientManager, exchangeId);

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, masterElectionExchangeHandler);

            logger.trace("Starting to elect master for request " + exchangeId);

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

            if (null == electionResult.getElectedMaster()) {
                logger.info("Another master is already working on a background sync. Stopping background sync");
                return;
            }

            logger.info("Elected client " + electionResult.getElectedMaster().getPeerAddress().inetAddress().getHostName() + ":" + electionResult.getElectedMaster().getPeerAddress().tcpPort() + " as master");
            logger.info("Stopping event aggregators on other clients");

            // send elected master to all clients
            InitSyncExchangeHandler initSyncExchangeHandler = new InitSyncExchangeHandler(
                    this.client,
                    this.clientManager,
                    this.eventAggregator,
                    exchangeId,
                    electionResult.getElectedMaster()
            );

            this.client.getObjectDataReplyHandler().addResponseCallbackHandler(exchangeId, initSyncExchangeHandler);

            Thread initSyncThread = new Thread(initSyncExchangeHandler);
            initSyncThread.setName("InitSyncExchangeHandler-" + exchangeId);
            initSyncThread.start();

            // await for init sync to complete
            try {
                initSyncExchangeHandler.await();
            } catch (InterruptedException e) {
                logger.error("Got interrupted while waiting for init of sync to complete. Message: " + e.getMessage(), e);
            }

            this.client.getObjectDataReplyHandler().removeResponseCallbackHandler(exchangeId);

            if (! initSyncExchangeHandler.isCompleted()) {
                logger.error("Init sync should be completed after awaiting. Aborting init sync process");
                return;
            }

            // event aggregator is started again in SyncCompleteRequestHandler

        } catch (Exception e) {
            logger.error("Got error in BlockingBackgroundSyncer. Message: " + e.getMessage(), e);
        }
    }
}
