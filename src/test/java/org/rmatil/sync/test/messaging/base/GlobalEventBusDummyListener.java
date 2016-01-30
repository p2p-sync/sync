package org.rmatil.sync.test.messaging.base;

import net.engio.mbassy.listener.Handler;
import org.rmatil.sync.core.eventbus.*;

import java.util.ArrayList;
import java.util.List;

public class GlobalEventBusDummyListener {

    protected List<IBusEvent> receivedBusEvents;

    public GlobalEventBusDummyListener() {
        this.receivedBusEvents = new ArrayList<>();
    }

    @Handler
    public void handleCreateBusEvent(CreateBusEvent createBusEvent) {
        this.receivedBusEvents.add(createBusEvent);
    }

    @Handler
    public void handleIgnoreBusEvent(IgnoreBusEvent ignoreBusEvent) {
        this.receivedBusEvents.add(ignoreBusEvent);
    }

    @Handler
    public void handleIgnoreObjectStoreUpdateBusEvent(IgnoreObjectStoreUpdateBusEvent ignoreObjectStoreUpdateBusEvent) {
        this.receivedBusEvents.add(ignoreObjectStoreUpdateBusEvent);
    }

    @Handler
    public void handleAddSharerToObjectStoreBusEvent(AddSharerToObjectStoreBusEvent addSharerToObjectStoreBusEvent) {
        this.receivedBusEvents.add(addSharerToObjectStoreBusEvent);
    }

    public void clear() {
        this.receivedBusEvents.clear();
    }

    public List<IBusEvent> getReceivedBusEvents() {
        return this.receivedBusEvents;
    }
}
