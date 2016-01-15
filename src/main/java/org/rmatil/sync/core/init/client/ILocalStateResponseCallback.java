package org.rmatil.sync.core.init.client;

import org.rmatil.sync.network.api.IResponseCallback;

import java.util.List;

public interface ILocalStateResponseCallback extends IResponseCallback {

    List<String> getAffectedFilePaths();
}
