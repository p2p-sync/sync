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
     * Whether the ip address is an IPv6 address
     */
    protected boolean isIpV6;

    /**
     * The port of the remote client
     */
    protected int port;

    /**
     * @param ipAddress The ip address of the remote client
     * @param isIpV6    Whether the ip address is an IPv6 address
     * @param port      The port of the remote client
     */
    public RemoteClientLocation(String ipAddress, boolean isIpV6, int port) {
        this.ipAddress = ipAddress;
        this.isIpV6 = isIpV6;
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
     * Whether the ip address returned by {@link RemoteClientLocation#getIpAddress()}
     * is an IPv6 address
     *
     * @return True, if it is an IPv6 address, false otherwise
     */
    public boolean isIpV6() {
        return isIpV6;
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
