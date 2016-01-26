package org.rmatil.sync.core.api;

import org.rmatil.sync.version.api.AccessType;

import java.nio.file.Path;

public interface IShareEvent {

    Path getRelativePath();

    AccessType getAccessType();

    String getUsernameToShareWith();
}
