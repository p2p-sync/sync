package org.rmatil.sync.core.init.client;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.network.api.IRequestCallback;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

/**
 * An extended interface which allows a request callback to
 * use the {@link ITreeStorageAdapter}, {@link IObjectStore},
 * {@link MBassador} and {@link IAccessManager} of the node
 * to handle the incoming request.
 */
public interface ILocalStateRequestCallback extends IRequestCallback {

    /**
     * Set the storage adapter of the node
     *
     * @param storageAdapter the storage adapter
     */
    void setStorageAdapter(ITreeStorageAdapter storageAdapter);

    /**
     * Set the object store of the node
     *
     * @param objectStore The object store
     */
    void setObjectStore(IObjectStore objectStore);

    /**
     * Set the global event bus of the node
     *
     * @param globalEventBus The global event bus
     */
    void setGlobalEventBus(MBassador<IBusEvent> globalEventBus);

    /**
     * Set the access manager of the node
     *
     * @param accessManager The access manager
     */
    void setAccessManager(IAccessManager accessManager);
}
