package org.rmatil.sync.core.model;

/**
 * A wrapper for connection details to another client machine
 */
public class RemoteClientLocation {

    /**
     * The ip address of the client
     */
    protected String ipAddress;

    /**
     * The port of the remote client
     */
    protected int port;

    /**
     * @param ipAddress The ip address of the remote client
     * @param port      The port of the remote client
     */
    public RemoteClientLocation(String ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    /**
     * Returns the ip address of the remote client
     *
     * @return The ip address
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * The port of the remote client
     *
     * @return The port
     */
    public int getPort() {
        return port;
    }
}
