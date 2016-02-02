package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.network.core.model.ClientDevice;

/**
 * Contains the result of a master election as
 * handled in {@link MasterElectionExchangeHandler}
 *
 * @deprecated As of 0.1. Will be removed in future releases.
 */
public class MasterElectionExchangeHandlerResult {

    /**
     * A flag indicating that a master is still working
     */
    protected boolean isMasterInProgress;

    /**
     * The master which is still working.
     * Only set, if {@link MasterElectionExchangeHandlerResult#isMasterInProgress} is true
     */
    protected ClientDevice masterInProgress;

    /**
     * The client device of the elected master
     */
    protected ClientDevice electedMaster;

    /**
     * @param electedMaster      The elected master
     * @param isMasterInProgress Set to true, if another master is still working
     * @param masterInProgress   Set the client device of the master which is still working
     */
    public MasterElectionExchangeHandlerResult(ClientDevice electedMaster, boolean isMasterInProgress, ClientDevice masterInProgress) {
        this.electedMaster = electedMaster;
        this.isMasterInProgress = isMasterInProgress;
        this.masterInProgress = masterInProgress;
    }

    /**
     * Returns the elected master client
     *
     * @return The elected master
     */
    public ClientDevice getElectedMaster() {
        return electedMaster;
    }

    /**
     * Returns a flag indicating that a master is still working
     *
     * @return True, if another master is still working
     */
    public boolean isMasterInProgress() {
        return isMasterInProgress;
    }

    /**
     * Returns the master which is still working.
     * Only set, if {@link MasterElectionExchangeHandlerResult#isMasterInProgress()} is true
     *
     * @return The master which is still working
     */
    public ClientDevice getMasterInProgress() {
        return masterInProgress;
    }
}
