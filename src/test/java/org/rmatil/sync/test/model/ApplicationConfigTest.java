package org.rmatil.sync.test.model;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.core.model.RemoteClientLocation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ApplicationConfigTest {

    private static ApplicationConfig    applicationConfig;
    private static RemoteClientLocation remoteClientLocation;
    private static List<String>         ignorePatterns;

    @BeforeClass
    public static void setUp()
            throws Exception {

        ignorePatterns = new ArrayList<>();

        remoteClientLocation = new RemoteClientLocation(
                "123.456.78.90",
                false,
                4003
        );

        applicationConfig = new ApplicationConfig(
                "user",
                "pass",
                "salt",
                0,
                0,
                0,
                0,
                4003,
                "~/.ssh/rsa_id.pub",
                "~/.ssh/rsa_id",
                remoteClientLocation,
                ignorePatterns
        );
    }

    @Test
    public void test() {
        assertEquals("user should be equal", "user", applicationConfig.getUserName());
        assertEquals("pass should be equal", "pass", applicationConfig.getPassword());
        assertEquals("salt should be equal", "salt", applicationConfig.getSalt());
        assertEquals("CacheTTl should be equal", 0, applicationConfig.getCacheTtl());
        assertEquals("PeerDiscoveryTimeout should be equal", 0, applicationConfig.getPeerDiscoveryTimeout());
        assertEquals("PeerBootstrapTimeout should be equal", 0, applicationConfig.getPeerBootstrapTimeout());
        assertEquals("ShutdownAnnounceTimeout should be equal", 0, applicationConfig.getShutdownAnnounceTimeout());
        assertEquals("DefaultPort should be equal", 4003, applicationConfig.getDefaultPort());
        assertEquals("PublicKeyPath should be equal", "~/.ssh/rsa_id.pub", applicationConfig.getPublicKeyPath());
        assertEquals("PrivateKeyPath should be equal", "~/.ssh/rsa_id", applicationConfig.getPrivateKeyPath());
        assertEquals("DefaultBootstrapLocation should be equal", "~/.ssh/rsa_id.pub", applicationConfig.getPublicKeyPath());
        assertEquals("RemoteClientLocation should be equal", remoteClientLocation, applicationConfig.getDefaultBootstrapLocation());
        assertEquals("IgnorePatterns should be equal", ignorePatterns, applicationConfig.getIgnorePatterns());
    }


}