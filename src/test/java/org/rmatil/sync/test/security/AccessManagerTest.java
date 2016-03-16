package org.rmatil.sync.test.security;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.security.AccessManager;
import org.rmatil.sync.core.security.IAccessManager;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.base.BaseTest;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.DeleteType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.api.PathType;
import org.rmatil.sync.version.core.model.Delete;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccessManagerTest extends BaseTest {

    protected static IObjectStore   objectStore;
    protected static IAccessManager accessManager;
    protected static final String TEST_FILE_NAME     = "myFile.txt";
    protected static final String OWNER_USER_NAME    = "owner";
    protected static final String SHARER_USER_NAME_1 = "sharer1";
    protected static final String SHARER_USER_NAME_2 = "sharer2";

    @BeforeClass
    public static void setUp()
            throws InputOutputException, IOException {
        createTestDirs();
        createObjectStoreDirs();
        objectStore = createObjectStore(new LocalStorageAdapter(ROOT_TEST_DIR1));

        Set<Sharer> sharers = new HashSet<>();
        sharers.add(new Sharer(
                SHARER_USER_NAME_1,
                AccessType.WRITE,
                new ArrayList<>()
        ));

        sharers.add(new Sharer(
                SHARER_USER_NAME_2,
                AccessType.READ,
                new ArrayList<>()
        ));

        PathObject pathObject = new PathObject(
                TEST_FILE_NAME,
                "",
                PathType.FILE,
                AccessType.WRITE,
                true,
                new Delete(DeleteType.EXISTENT, new ArrayList<>()),
                OWNER_USER_NAME,
                sharers,
                new ArrayList<>()
        );

        objectStore.getObjectManager().writeObject(pathObject);

        accessManager = new AccessManager(objectStore);
    }

    @AfterClass
    public static void tearDown() {
        deleteTestDirs();
    }

    @Test
    public void testAccessManager()
            throws InputOutputException {

        assertTrue("Owner should have write permissions", accessManager.hasAccess(OWNER_USER_NAME, AccessType.WRITE, TEST_FILE_NAME));
        assertTrue("Owner should have read permissions", accessManager.hasAccess(OWNER_USER_NAME, AccessType.READ, TEST_FILE_NAME));
        assertFalse("Owner should not have access removed permissions", accessManager.hasAccess(OWNER_USER_NAME, AccessType.ACCESS_REMOVED, TEST_FILE_NAME));

        assertTrue("User1 should have write permissions", accessManager.hasAccess(SHARER_USER_NAME_1, AccessType.WRITE, TEST_FILE_NAME));
        assertTrue("User1 should have read permissions", accessManager.hasAccess(SHARER_USER_NAME_1, AccessType.READ, TEST_FILE_NAME));
        assertTrue("User1 should have access removed permissions", accessManager.hasAccess(SHARER_USER_NAME_1, AccessType.ACCESS_REMOVED, TEST_FILE_NAME));

        assertFalse("User2 should not have write permissions", accessManager.hasAccess(SHARER_USER_NAME_2, AccessType.WRITE, TEST_FILE_NAME));
        assertTrue("User2 should have read permissions", accessManager.hasAccess(SHARER_USER_NAME_2, AccessType.READ, TEST_FILE_NAME));
        assertTrue("User2 should have access removed permissions", accessManager.hasAccess(SHARER_USER_NAME_2, AccessType.ACCESS_REMOVED, TEST_FILE_NAME));
    }
}
