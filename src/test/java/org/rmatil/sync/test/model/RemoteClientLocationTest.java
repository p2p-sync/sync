package org.rmatil.sync.test.model;

import org.junit.Test;
import org.rmatil.sync.core.model.RemoteClientLocation;

import static org.junit.Assert.assertEquals;

public class RemoteClientLocationTest {

    protected static final String IP_ADDRESS = "127.0.0.1";
    protected static final int    PORT       = 4003;

    @Test
    public void test() {
        RemoteClientLocation remoteClientLocation = new RemoteClientLocation(
                IP_ADDRESS,
                PORT
        );

        assertEquals("IP address is not equal", IP_ADDRESS, remoteClientLocation.getIpAddress());
        assertEquals("Port is not equal", PORT, remoteClientLocation.getPort());
    }
}
