package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * The interface for all events which should be used
 * in the global event bus
 */
public interface IBusEvent {

    /**
     * Returns the event to propagate
     *
     * @return The event to propagate
     */
    IEvent getEvent();

}
