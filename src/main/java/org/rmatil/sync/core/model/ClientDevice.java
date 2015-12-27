package org.rmatil.sync.core.model;

import net.tomp2p.peers.PeerAddress;

import java.io.Serializable;
import java.util.UUID;

/**
 * A wrapper for representing some particular data
 * about the client of a user.
 */
public class ClientDevice implements Serializable {

    /**
     * The user name of the user to which this client belongs
     */
    protected String userName;

    /**
     * The unique id of this client
     */
    protected UUID clientDeviceId;

    /**
     * The address of this client
     */
    protected PeerAddress peerAddress;

    /**
     * @param userName       The user name of the user to which this client belongs
     * @param clientDeviceId The unique client id
     * @param peerAddress    The address of the client
     */
    public ClientDevice(String userName, UUID clientDeviceId, PeerAddress peerAddress) {
        this.userName = userName;
        this.clientDeviceId = clientDeviceId;
        this.peerAddress = peerAddress;
    }

    /**
     * Returns the user name of the user to which this client belongs
     *
     * @return The user name
     */
    public String getUserName() {
        return userName;
    }

    /**
     * The unique client device id
     *
     * @return The client id
     */
    public UUID getClientDeviceId() {
        return clientDeviceId;
    }

    /**
     * The address of this client device
     *
     * @return The address of this client
     */
    public PeerAddress getPeerAddress() {
        return peerAddress;
    }

    /**
     * Sets the client's address
     *
     * @param peerAddress The address to set
     */
    public void setPeerAddress(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
    }
}
