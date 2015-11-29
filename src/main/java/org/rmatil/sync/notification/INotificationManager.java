package org.rmatil.sync.notification;

import org.rmatil.sync.locations.api.ILocation;

import java.util.List;

public interface INotificationManager {

    /**
     * Send the given notification to all online clients of
     * the user having this UserProfile
     *
     * @param notification A notification to send to all clients
     */
    void notifyAll(INotification notification);

    /**
     * Send the given notification to the given client
     *
     * @param clientLocation The location of the client
     * @param notification   The notification to send to the client
     */
    void notifyClient(ILocation clientLocation, INotification notification);

    /**
     * Send the given notification to a list of clients
     *
     * @param clientLocations The list of clients to which the notificaiton should be send
     * @param notification    The notification to send
     */
    void notifyClients(List<ILocation> clientLocations, INotification notification);
}
