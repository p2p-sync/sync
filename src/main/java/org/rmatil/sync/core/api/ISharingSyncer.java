package org.rmatil.sync.core.api;

import org.rmatil.sync.core.exception.SharingFailedException;

public interface ISharingSyncer {

    /**
     * Syncs the given sharing event
     *
     * @param sharingEvent The sharing event to sync
     */
    void sync(IShareEvent sharingEvent) throws SharingFailedException;
}
