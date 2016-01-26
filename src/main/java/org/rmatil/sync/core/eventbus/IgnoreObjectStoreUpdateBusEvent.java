package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

/**
 * An {@link IBusEvent} which indicates that the provided
 * event should be ignored during updating of the object store
 */
public class IgnoreObjectStoreUpdateBusEvent implements IBusEvent {

    /**
     * The event to ignore
     */
    protected IEvent event;

    /**
     * @param event The event to ignore during the updating the object store
     */
    public IgnoreObjectStoreUpdateBusEvent(IEvent event) {
        this.event = event;
    }

    @Override
    public IEvent getEvent() {
        return this.event;
    }

}
