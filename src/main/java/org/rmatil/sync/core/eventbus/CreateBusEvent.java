package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * An {@link IBusEvent} which indicates that a path has been
 * created on disk and should be synchronized
 */
public class CreateBusEvent implements IBusEvent {

    /**
     * The event to propagate, generally a {@link org.rmatil.sync.event.aggregator.core.events.CreateEvent}
     */
    protected IEvent event;

    /**
     * @param event The create event to propagate, generally a {@link org.rmatil.sync.event.aggregator.core.events.CreateEvent}
     */
    public CreateBusEvent(IEvent event) {
        this.event = event;
    }

    @Override
    public IEvent getEvent() {
        return event;
    }
}
