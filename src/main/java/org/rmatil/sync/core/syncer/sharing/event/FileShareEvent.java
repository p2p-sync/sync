package org.rmatil.sync.core.syncer.sharing.event;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.version.api.AccessType;

import java.nio.file.Path;

public class FileShareEvent implements IShareEvent {

    protected Path relativePath;

    protected AccessType accessType;

    protected String usernameToShareWith;

    public FileShareEvent(Path relativePath, AccessType accessType, String usernameToShareWith) {
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
