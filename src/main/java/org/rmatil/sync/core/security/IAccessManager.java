package org.rmatil.sync.core.security;

import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;

public interface IAccessManager {

    /**
     * Checks whether the given client device has access
     * to the given access type on the specified path
     *
     * @param sharerUsername The username to check for access
     * @param accessType     The access type to check
     * @param filePath       The file path for which to check the access
     *
     * @return True, if the sharer has access. False otherwise.
     *
     * @throws InputOutputException If reading the ObjectStore failed or no path object was found for the given file path
     */
    boolean hasAccess(String sharerUsername, AccessType accessType, String filePath)
            throws InputOutputException;
}
