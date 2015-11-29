package org.rmatil.sync.api;

import org.rmatil.sync.locations.api.ILocationManager;
import org.rmatil.sync.notification.INotificationManager;


public interface IUserProfile {

    /**
     * Returns a merge of the given UserProfile with this UserProfile.
     *
     * @param userProfile The userProfile to merge with this one
     *
     * @return The merged UserProfile
     */
    IUserProfile merge(IUserProfile userProfile);

    /**
     * Get the location manager to handle the user's client locations
     *
     * @return The LocationManager to handle the user's client locations
     */
    ILocationManager getLocationManager();

    /**
     * Get the notification manager to send notifications to the clients of this UserProfile
     *
     * @return The notification manager
     */
    INotificationManager getNotificationManager();
}
