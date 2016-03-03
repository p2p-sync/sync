package org.rmatil.sync.test.syncer.sharing.event;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.syncer.sharing.event.UnshareEvent;
import org.rmatil.sync.version.api.AccessType;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

public class UnshareEventTest {

    private static final Path       PATH        = Paths.get("myFile");
    private static final AccessType ACCESS_TYPE = AccessType.WRITE;
    private static final String     USER        = "Lance Bogrol";

    private static UnshareEvent unshareEvent;

    @BeforeClass
    public static void setUp() {
        unshareEvent = new UnshareEvent(
                PATH,
                ACCESS_TYPE,
                USER
        );
    }

    @Test
    public void testAccessor() {
        assertEquals("path is not equal", PATH, unshareEvent.getRelativePath());
        assertEquals("access type is not equal", ACCESS_TYPE, unshareEvent.getAccessType());
        assertEquals("User is not equal", USER, unshareEvent.getUsernameToShareWith());
    }
}
