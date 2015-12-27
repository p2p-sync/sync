package org.rmatil.sync.core.syncer;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * A synchronizer which propagates file system events to other clients
 */
public interface ISyncer {

    /**
     * Syncs the given event to other clients
     *
     * @param event The event to sync
     */
    void sync(IEvent event);

}
