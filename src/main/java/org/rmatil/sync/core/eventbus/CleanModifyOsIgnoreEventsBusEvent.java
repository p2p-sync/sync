package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;

public class CleanModifyOsIgnoreEventsBusEvent implements IBusEvent {

    protected String relativePath;

    public CleanModifyOsIgnoreEventsBusEvent(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public IEvent getEvent() {
        return null;
    }
}
