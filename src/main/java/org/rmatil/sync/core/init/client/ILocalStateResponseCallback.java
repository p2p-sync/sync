package org.rmatil.sync.core.init.client;

import org.rmatil.sync.network.api.IResponseCallback;

import java.util.List;

/**
 * An extended {@link IResponseCallback} which allows the
 * implementations to specify file paths which are affected
 * at the moment their operation. Any other incoming
 * request affecting also an entry of the specified affected
 * file paths will be denied.
 */
public interface ILocalStateResponseCallback extends IResponseCallback {

    /**
     * Get a list of file paths which are affected
     * during the execution of the operation.
     *
     * @return The list of affected file paths
     */
    List<String> getAffectedFilePaths();
}
