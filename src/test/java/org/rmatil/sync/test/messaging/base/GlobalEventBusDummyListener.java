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
    public synchronized void handleCreateBusEvent(CreateBusEvent createBusEvent) {
        this.receivedBusEvents.add(createBusEvent);
    }

    @Handler
    public synchronized void handleIgnoreBusEvent(IgnoreBusEvent ignoreBusEvent) {
        this.receivedBusEvents.add(ignoreBusEvent);
    }

    @Handler
    public synchronized void handleIgnoreObjectStoreUpdateBusEvent(IgnoreObjectStoreUpdateBusEvent ignoreObjectStoreUpdateBusEvent) {
        this.receivedBusEvents.add(ignoreObjectStoreUpdateBusEvent);
    }

    @Handler
    public synchronized void handleAddSharerToObjectStoreBusEvent(AddSharerToObjectStoreBusEvent addSharerToObjectStoreBusEvent) {
        this.receivedBusEvents.add(addSharerToObjectStoreBusEvent);
    }

    @Handler
    public synchronized void handlAddOwnerAndAccessTypeToObjectStoreBusEvent(AddOwnerAndAccessTypeToObjectStoreBusEvent addOwnerAndAccessTypeToObjectStoreBusEvent) {
        this.receivedBusEvents.add(addOwnerAndAccessTypeToObjectStoreBusEvent);
    }

    public synchronized void clear() {
        this.receivedBusEvents.clear();
    }

    public synchronized List<IBusEvent> getReceivedBusEvents() {
        return this.receivedBusEvents;
    }
}
