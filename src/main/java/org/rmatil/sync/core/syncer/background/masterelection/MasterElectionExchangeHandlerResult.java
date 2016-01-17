package org.rmatil.sync.core.syncer.background.masterelection;

import org.rmatil.sync.network.core.model.ClientDevice;

public class MasterElectionExchangeHandlerResult {

    /**
     * The client device of the elected master
     */
    protected ClientDevice electedMaster;

    public MasterElectionExchangeHandlerResult(ClientDevice electedMaster) {
        this.electedMaster = electedMaster;
    }

    public ClientDevice getElectedMaster() {
        return electedMaster;
    }
}
