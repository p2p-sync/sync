package org.rmatil.sync.core.init.client;

import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.INodeManager;

/**
 * An interface for a {@link ILocalStateRequestCallback}
 * which additionally uses a {@link INodeManager} and a {@link IEventAggregator}.
 */
public interface IExtendedLocalStateRequestCallback extends ILocalStateRequestCallback {

    /**
     * Set the node manager used on the node
     *
     * @param nodeManager The node manager
     */
    void setClientManager(INodeManager nodeManager);

    /**
     * Set the event aggregator of the node
     *
     * @param eventAggregator The event aggregator
     */
    void setEventAggregator(IEventAggregator eventAggregator);

}
