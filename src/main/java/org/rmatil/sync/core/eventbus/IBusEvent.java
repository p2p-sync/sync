package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

public interface IBusEvent {

    IEvent getEvent();

}
