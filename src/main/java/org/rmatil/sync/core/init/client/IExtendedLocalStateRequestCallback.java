package org.rmatil.sync.core.init.client;

import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;

public interface IExtendedLocalStateRequestCallback extends ILocalStateRequestCallback {

    void setClientManager(IClientManager clientManager);

    void setEventAggregator(IEventAggregator eventAggregator);

}
