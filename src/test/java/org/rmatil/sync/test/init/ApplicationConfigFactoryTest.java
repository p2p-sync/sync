package org.rmatil.sync.test.init;

import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.init.ApplicationConfigFactory;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.core.model.RemoteClientLocation;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ApplicationConfigFactoryTest {

    private static final String USERNAME = "Chauffina Carr";
    private static final String PASSWORD = "ThisIsSafeUseIt";
    private static final String SALT     = "SaltAndPepperMakesTheMealBetter";

    private static RSAPublicKey         publicKey;
    private static RSAPrivateKey        privateKey;
    private static RemoteClientLocation remote;
    private static List<String>         ignorePatterns;

    @BeforeClass
    public static void setUp()
            throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.genKeyPair();

        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();

        remote = new RemoteClientLocation(
                "192.168.1.1",
                4003
        );

        ignorePatterns = ApplicationConfigFactory.getDefaultIgnorePatterns();
    }

    @Test
    public void testCreateBootstrapApplicationConfig()
            throws NoSuchAlgorithmException {
        ApplicationConfig appConfig = ApplicationConfigFactory.createBootstrapApplicationConfig();

        assertNull("Username should be null", appConfig.getUserName());
        assertNull("Password should be null", appConfig.getPassword());
        assertNull("Salt should be null", appConfig.getSalt());

        assertNotNull("Public key should not be null", appConfig.getPublicKey());
        assertNotNull("Private key should not be null", appConfig.getPrivateKey());

        assertEquals("CacheTtl should be equal", Config.DEFAULT.getCacheTtl(), appConfig.getCacheTtl());
        assertEquals("PeerDiscoveryTimeout should be equal", Config.DEFAULT.getPeerDiscoveryTimeout(), appConfig.getPeerDiscoveryTimeout());
        assertEquals("PeerBootstrapTimeout should be equal", Config.DEFAULT.getPeerBootstrapTimeout(), appConfig.getPeerBootstrapTimeout());
        assertEquals("ShutdownAnnounceTimeout should be equal", Config.DEFAULT.getShutdownAnnounceTimeout(), appConfig.getShutdownAnnounceTimeout());

        assertNull("Remote Client location should be null", appConfig.getBootstrapLocation());

        assertThat("Ignore patterns should be equal", ApplicationConfigFactory.getDefaultIgnorePatterns(), is(appConfig.getIgnorePatterns()));
    }

    @Test
    public void testCreateDefaultApplicationConfiguration() {
        ApplicationConfig appConfig = ApplicationConfigFactory.createDefaultApplicationConfig(
                USERNAME,
                PASSWORD,
                SALT,
                publicKey,
                privateKey,
                remote,
                ignorePatterns
        );

        assertEquals("Username should be equal", USERNAME, appConfig.getUserName());
        assertEquals("Password should be equal", PASSWORD, appConfig.getPassword());
        assertEquals("Salt should be equal", SALT, appConfig.getSalt());

        assertEquals("PublicKey should equal", publicKey, appConfig.getPublicKey());
        assertEquals("PrivateKey should be equal", privateKey, appConfig.getPrivateKey());

        assertEquals("CacheTtl should be equal", Config.DEFAULT.getCacheTtl(), appConfig.getCacheTtl());
        assertEquals("PeerDiscoveryTimeout should be equal", Config.DEFAULT.getPeerDiscoveryTimeout(), appConfig.getPeerDiscoveryTimeout());
        assertEquals("PeerBootstrapTimeout should be equal", Config.DEFAULT.getPeerBootstrapTimeout(), appConfig.getPeerBootstrapTimeout());
        assertEquals("ShutdownAnnounceTimeout should be equal", Config.DEFAULT.getShutdownAnnounceTimeout(), appConfig.getShutdownAnnounceTimeout());

        assertEquals("Remote should be equal", remote, appConfig.getBootstrapLocation());

        assertThat("Ignore patterns should be equal", ignorePatterns, is(appConfig.getIgnorePatterns()));
    }
}
