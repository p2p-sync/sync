package org.rmatil.sync.core.syncer.sharing.event;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.version.api.AccessType;

import java.nio.file.Path;

/**
 * An event indicating that the specified path
 * should be unshared with the configured user.
 * The user should receive the access type set.
 *
 * @see org.rmatil.sync.core.syncer.sharing.SharingSyncer
 */
public class UnshareEvent implements IShareEvent {

    protected Path relativePath;

    protected AccessType accessType;

    protected String usernameToShareWith;

    public UnshareEvent(Path relativePath, AccessType accessType, String usernameToShareWith) {
        this.relativePath = relativePath;
        this.accessType = accessType;
        this.usernameToShareWith = usernameToShareWith;
    }

    @Override
    public Path getRelativePath() {
        return this.relativePath;
    }

    @Override
    public AccessType getAccessType() {
        return accessType;
    }

    @Override
    public String getUsernameToShareWith() {
        return usernameToShareWith;
    }
}
