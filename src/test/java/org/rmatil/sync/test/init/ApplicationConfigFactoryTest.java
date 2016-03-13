package org.rmatil.sync.test.init;

import org.junit.Test;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.init.ApplicationConfigFactory;
import org.rmatil.sync.core.model.ApplicationConfig;

import java.security.NoSuchAlgorithmException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ApplicationConfigFactoryTest {

    @Test
    public void testCreateDefaultApplicationConfig()
            throws NoSuchAlgorithmException {
        ApplicationConfig appConfig = ApplicationConfigFactory.createDefaultApplicationConfig();

        assertNull("Username should be null", appConfig.getUserName());
        assertNull("Password should be null", appConfig.getPassword());
        assertNull("Salt should be null", appConfig.getSalt());

        assertNotNull("Public key should not be null", appConfig.getPublicKey());
        assertNotNull("Private key should not be null", appConfig.getPrivateKey());

        assertEquals("CacheTtl should be equal", Config.DEFAULT.getCacheTtl(), appConfig.getCacheTtl());
        assertEquals("PeerDiscoveryTimeout should be equal", Config.DEFAULT.getPeerDiscoveryTimeout(), appConfig.getPeerDiscoveryTimeout());
        assertEquals("PeerBootstrapTimeout should be equal", Config.DEFAULT.getPeerBootstrapTimeout(), appConfig.getPeerBootstrapTimeout());
        assertEquals("ShutdownAnnounceTimeout should be equal", Config.DEFAULT.getShutdownAnnounceTimeout(), appConfig.getShutdownAnnounceTimeout());

        assertNotNull("Remote Client location should not be null", appConfig.getBootstrapLocation());
        assertNull("Bootstrap IP should be null", appConfig.getBootstrapLocation().getIpAddress());
        assertEquals("Bootstrap Port should be equals", - 1, appConfig.getBootstrapLocation().getPort());

        assertThat("Ignore patterns should be equal", ApplicationConfigFactory.getDefaultIgnorePatterns(), is(appConfig.getIgnorePatterns()));

    }
}
