package org.rmatil.sync.core.init.client;

import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.api.INodeManager;

public interface IExtendedLocalStateRequestCallback extends ILocalStateRequestCallback {

    void setClientManager(INodeManager nodeManager);

    void setEventAggregator(IEventAggregator eventAggregator);

}
