package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * An {@link IBusEvent} which indicates that the provided
 * event should be ignored during synchronization
 */
public class IgnoreBusEvent implements IBusEvent {

    /**
     * The event to ignore
     */
    protected IEvent event;

    /**
     * @param event The event to ignore during the synchronization
     */
    public IgnoreBusEvent(IEvent event) {
        this.event = event;
    }

    @Override
    public IEvent getEvent() {
        return this.event;
    }
}
