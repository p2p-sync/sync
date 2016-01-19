package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.network.core.model.ClientDevice;

/**
 * Contains the result of a master election as
 * handled in {@link MasterElectionExchangeHandler}
 */
public class MasterElectionExchangeHandlerResult {

    /**
     * The client device of the elected master
     */
    protected ClientDevice electedMaster;

    /**
     * @param electedMaster The elected master
     */
    public MasterElectionExchangeHandlerResult(ClientDevice electedMaster) {
        this.electedMaster = electedMaster;
    }

    /**
     * Returns the elected master client
     *
     * @return The elected master
     */
    public ClientDevice getElectedMaster() {
        return electedMaster;
    }
}
