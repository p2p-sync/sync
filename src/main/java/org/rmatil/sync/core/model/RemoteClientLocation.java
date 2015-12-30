package org.rmatil.sync.core.model;

public class RemoteClientLocation {

    protected String ipAddress;

    protected boolean isIpV6;

    protected int port;

    public RemoteClientLocation(String ipAddress, boolean isIpV6, int port) {
        this.ipAddress = ipAddress;
        this.isIpV6 = isIpV6;
        this.port = port;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public boolean isIpV6() {
        return isIpV6;
    }

    public int getPort() {
        return port;
    }
}
