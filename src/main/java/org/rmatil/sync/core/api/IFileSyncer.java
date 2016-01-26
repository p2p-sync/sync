package org.rmatil.sync.core.api;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * A synchroniser which propagates file system events to other clients
 */
public interface IFileSyncer {

    /**
     * Syncs the given event to other clients
     *
     * @param event The event to sync
     */
    void sync(IEvent event);

}
