package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

public class IgnoreBusEvent implements IBusEvent {

    protected IEvent event;

    public IgnoreBusEvent(IEvent event) {
        this.event = event;
    }

    @Override
    public IEvent getEvent() {
        return this.event;
    }
}
