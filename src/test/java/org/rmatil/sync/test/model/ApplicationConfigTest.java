package org.rmatil.sync.test.model;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.core.model.RemoteClientLocation;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ApplicationConfigTest {

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    private static ApplicationConfig    applicationConfig;
    private static RemoteClientLocation remoteClientLocation;
    private static List<String>         ignorePatterns;
    private static RSAPublicKey         rsaPublicKey;
    private static RSAPrivateKey        rsaPrivateKey;

    @BeforeClass
    public static void setUp()
            throws Exception {

        KeyPairGenerator keyPairGenerator;
        keyPairGenerator = KeyPairGenerator.getInstance("RSA");

        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        rsaPublicKey = (RSAPublicKey) keyPair.getPublic();
        rsaPrivateKey = (RSAPrivateKey) keyPair.getPrivate();

        ignorePatterns = new ArrayList<>();

        remoteClientLocation = new RemoteClientLocation(
                "123.456.78.90",
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
                rsaPublicKey,
                rsaPrivateKey,
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
        assertEquals("DefaultPort should be equal", 4003, applicationConfig.getPort());
        assertEquals("PublicKey should be equal", rsaPublicKey, applicationConfig.getPublicKey());
        assertEquals("PrivateKey should be equal", rsaPrivateKey, applicationConfig.getPrivateKey());
        assertEquals("RemoteClientLocation should be equal", remoteClientLocation, applicationConfig.getBootstrapLocation());
        assertEquals("IgnorePatterns should be equal", ignorePatterns, applicationConfig.getIgnorePatterns());
    }

    @Test
    public void testIllegalArgumentPublicKey() {
        thrown.expect(IllegalArgumentException.class);

        ApplicationConfig applicationConfig = new ApplicationConfig(
                "user",
                "salt",
                "salt",
                0,
                0,
                0,
                0,
                0,
                null,
                rsaPrivateKey,
                remoteClientLocation,
                ignorePatterns
        );
    }

    @Test
    public void testIllegalArgumentPrivateKey() {
        thrown.expect(IllegalArgumentException.class);

        ApplicationConfig applicationConfig = new ApplicationConfig(
                "user",
                "salt",
                "salt",
                0,
                0,
                0,
                0,
                0,
                rsaPublicKey,
                null,
                remoteClientLocation,
                ignorePatterns
        );
    }

    @Test
    public void testSerialisation() {
        String json = applicationConfig.toJson();

        ApplicationConfig actual = ApplicationConfig.fromJson(json);

        assertEquals("user should be equal", applicationConfig.getUserName(), actual.getUserName());
        assertEquals("pass should be equal", applicationConfig.getPassword(), actual.getPassword());
        assertEquals("salt should be equal", applicationConfig.getSalt(), actual.getSalt());
        assertEquals("CacheTTl should be equal", applicationConfig.getCacheTtl(), actual.getCacheTtl());
        assertEquals("PeerDiscoveryTimeout should be equal", applicationConfig.getPeerDiscoveryTimeout(), actual.getPeerDiscoveryTimeout());
        assertEquals("PeerBootstrapTimeout should be equal", applicationConfig.getPeerBootstrapTimeout(), actual.getPeerBootstrapTimeout());
        assertEquals("ShutdownAnnounceTimeout should be equal", applicationConfig.getShutdownAnnounceTimeout(), actual.getShutdownAnnounceTimeout());
        assertEquals("DefaultPort should be equal", applicationConfig.getPort(), actual.getPort());
        assertArrayEquals("PublicKey should be equal", applicationConfig.getPublicKey().getEncoded(), actual.getPublicKey().getEncoded());
        assertArrayEquals("PrivateKey should be equal", applicationConfig.getPrivateKey().getEncoded(), actual.getPrivateKey().getEncoded());
        assertNotNull("RemoteClientLocation should not be null", actual.getBootstrapLocation());
        assertEquals("IP should be equal", applicationConfig.getBootstrapLocation().getIpAddress(), actual.getBootstrapLocation().getIpAddress());
        assertEquals("Port should be equal", applicationConfig.getBootstrapLocation().getPort(), actual.getBootstrapLocation().getPort());
        assertThat("Ignore patterns should be equal", applicationConfig.getIgnorePatterns(), is(actual.getIgnorePatterns()));
    }

}