package org.rmatil.sync.test.syncer.file;

import org.rmatil.sync.core.api.IFileSyncer;
import org.rmatil.sync.event.aggregator.core.events.IEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DummyFileSyncer implements IFileSyncer {

    private ConcurrentLinkedQueue<IEvent> events = new ConcurrentLinkedQueue<>();

    @Override
    public void sync(IEvent event) {
        this.events.add(event);
    }

    public ConcurrentLinkedQueue<IEvent> getLinkedQueue() {
        return events;
    }
}
