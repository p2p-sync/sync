package org.rmatil.sync.core.messaging.fileexchange.move;

import org.rmatil.sync.core.messaging.fileexchange.base.ARequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;

import java.util.ArrayList;
import java.util.UUID;

public class FileMoveRequest extends ARequest {

    protected String oldPath;
    protected String newPath;

    protected boolean isFile;

    public FileMoveRequest(UUID exchangeId, ClientDevice clientDevice, ClientLocation receiverAddress, String oldPath, String newPath, boolean isFile) {
        super(exchangeId, clientDevice, new ArrayList<>());
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.isFile = isFile;

        super.receiverAddresses.add(receiverAddress);
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public boolean isFile() {
        return isFile;
    }
}
