package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.Set;

public class AddSharerToObjectStoreBusEvent implements IBusEvent {

    protected String relativeFilePath;

    protected Set<Sharer> sharers;

    public AddSharerToObjectStoreBusEvent(String relativeFilePath, Set<Sharer> sharers) {
        this.relativeFilePath = relativeFilePath;
        this.sharers = sharers;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    public Set<Sharer> getSharers() {
        return sharers;
    }

    @Override
    public IEvent getEvent() {
        return null;
    }
}
