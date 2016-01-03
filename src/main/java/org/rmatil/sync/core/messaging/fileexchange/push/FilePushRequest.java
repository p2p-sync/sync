package org.rmatil.sync.core.messaging.fileexchange.push;

import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.api.IResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.version.core.model.Version;

import java.util.List;
import java.util.UUID;

public class FilePushRequest implements IRequest {

    protected UUID exchangeId;
    protected ClientDevice clientDevice;

    protected String relativeFilePath;

    protected Version fileVersion;

    public FilePushRequest(UUID exchangeId, ClientDevice clientDevice, String relativeFilePath, Version fileVersion) {
        this.exchangeId = exchangeId;
        this.clientDevice = clientDevice;
        this.relativeFilePath = relativeFilePath;
        this.fileVersion = fileVersion;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    public Version getFileVersion() {
        return fileVersion;
    }

    @Override
    public List<ClientLocation> getReceiverAddresses() {
        return null;
    }

    @Override
    public void setClient(IClient iClient) {

    }

    @Override
    public UUID getExchangeId() {
        return this.exchangeId;
    }

    @Override
    public ClientDevice getClientDevice() {
        return this.clientDevice;
    }

    @Override
    public void sendResponse(IResponse iResponse) {

    }

    @Override
    public void run() {

    }
}
