package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.network.api.IRequestCallback;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

public interface ILocalStateRequestCallback extends IRequestCallback {

    void setStorageAdapter(IStorageAdapter storageAdapter);

    void setObjectStore(IObjectStore objectStore);

    void setGlobalEventBus(MBassador<IBusEvent> globalEventBus);
}
