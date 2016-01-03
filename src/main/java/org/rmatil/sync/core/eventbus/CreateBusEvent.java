package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

public class CreateBusEvent implements IBusEvent {

    protected IEvent event;

    public CreateBusEvent(IEvent event) {
        this.event = event;
    }

    @Override
    public IEvent getEvent() {
        return event;
    }
}
