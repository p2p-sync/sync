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

        if (pathObject.getOwner().equals(sharerUsername)) {
            // owner has access to all access types but the removed access one
            return AccessType.ACCESS_REMOVED != accessType;

        }

        List<AccessType> types = Arrays.asList(AccessType.values());
        for (Sharer entry : pathObject.getSharers()) {
            // check whether the access type and the username is equal
            if (entry.getAccessType().equals(accessType) && entry.getUsername().equals(sharerUsername)) {
                return true;
            }

            // check whether the access type of the sharer has higher "rights" than the given one
            if (types.indexOf(entry.getAccessType()) >= types.indexOf(accessType) && entry.getUsername().equals(sharerUsername)) {
                return true;
            }
        }

        return false;
    }
}
