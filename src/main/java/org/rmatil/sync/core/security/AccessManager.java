package org.rmatil.sync.core.security;

import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.util.Arrays;
import java.util.List;

/**
 * Provides utilities to check access of particular users
 * for certain files
 */
public class AccessManager implements IAccessManager {

    /**
     * The ObjectStore to use to check access
     */
    protected IObjectStore objectStore;

    /**
     * @param objectStore The ObjectStore to use to check access
     */
    public AccessManager(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public boolean hasAccess(String sharerUsername, AccessType accessType, String filePath)
            throws InputOutputException {
        PathObject pathObject = this.objectStore.getObjectManager().getObjectForPath(filePath);

        if (null == pathObject) {
            throw new InputOutputException("Could not find pathObject for " + filePath);
        }

        // Note that the owner can be null:
        // client 2 shares with client 1
        // client 1 makes a change and propagates to client 2
        // client 2 is owner for file and checks access -> NullPointer!
        if (sharerUsername.equals(pathObject.getOwner())) {
            // owner has access to all access types but the removed access one
            return AccessType.ACCESS_REMOVED != accessType;

        }

        List<AccessType> types = Arrays.asList(AccessType.values());
        for (Sharer entry : pathObject.getSharers()) {
            // check whether the access type and the username is equal
            if (accessType.equals(entry.getAccessType()) && sharerUsername.equals(entry.getUsername())) {
                return true;
            }

            // check whether the access type of the sharer has higher "rights" than the given one
            if (types.indexOf(entry.getAccessType()) >= types.indexOf(accessType) && sharerUsername.equals(entry.getUsername())) {
                return true;
            }
        }

        return false;
    }
}
