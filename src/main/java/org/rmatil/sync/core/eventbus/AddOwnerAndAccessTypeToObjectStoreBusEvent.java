package org.rmatil.sync.core.eventbus;

import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.version.api.AccessType;

public class AddOwnerAndAccessTypeToObjectStoreBusEvent implements IBusEvent {

    protected String owner;
    protected AccessType accessType;
    protected String relativeFilePath;

    public AddOwnerAndAccessTypeToObjectStoreBusEvent(String owner, AccessType accessType, String relativeFilePath) {
        this.owner = owner;
        this.accessType = accessType;
        this.relativeFilePath = relativeFilePath;
    }

    public String getOwner() {
        return owner;
    }

    public AccessType getAccessType() {
        return accessType;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    @Override
    public IEvent getEvent() {
        return null;
    }
}
