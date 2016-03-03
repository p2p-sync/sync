package org.rmatil.sync.core.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

/**
 * The application config wrapper.
 * Holds configuration values to setup a new instance of {@link org.rmatil.sync.core.Sync}
 */
public class ApplicationConfig {

    protected static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    /**
     * The name of the user
     */
    protected String userName;

    /**
     * The password of the user
     */
    protected String password;

    /**
     * The salt corresponding to the password
     */
    protected String salt;

    /**
     * The live time in milliseconds for cache values.
     */
    protected long cacheTtl;

    /**
     * The timeout in milliseconds used until peer discovery
     * should be successful
     */
    protected long peerDiscoveryTimeout;

    /**
     * The amount of milliseconds until which the bootstrap
     * process should be successful
     */
    protected long peerBootstrapTimeout;

    /**
     * How many milliseconds should be waited before
     * the client is unfriendly shutdown
     */
    protected long shutdownAnnounceTimeout;

    /**
     * The default port on which the client should
     * be started
     */
    protected int defaultPort;

    /**
     * The path to the public key of the user
     */
    protected String publicKeyPath;

    /**
     * The path to the private key of the user
     */
    protected String privateKeyPath;

    /**
     * The default bootstrap locations
     */
    protected RemoteClientLocation defaultBootstrapLocation;

    /**
     * A list of file glob patterns which should be ignored
     * during sync
     */
    protected List<String> ignorePatterns;

    /**
     * @param userName                 The name of the user
     * @param password                 The password of the user
     * @param salt                     The salt of the user
     * @param cacheTtl                 The live time in milliseconds for cache values.
     * @param peerDiscoveryTimeout     The timeout in milliseconds used until peer discovery should be successful
     * @param peerBootstrapTimeout     The amount of milliseconds until which the bootstrap process should be successful
     * @param shutdownAnnounceTimeout  How many milliseconds should be waited before the client is unfriendly shutdown
     * @param defaultPort              The default port on which the client should be started
     * @param publicKeyPath            The path to the public key of the user
     * @param privateKeyPath           The path to the private key of the user
     * @param defaultBootstrapLocation The default bootstrap location to which the client should be connected on startup. May be null
     * @param ignorePatterns           A list of file glob patterns which are ignored during sync
     */
    public ApplicationConfig(String userName, String password, String salt, long cacheTtl, long peerDiscoveryTimeout, long peerBootstrapTimeout, long shutdownAnnounceTimeout, int defaultPort, String publicKeyPath, String privateKeyPath, RemoteClientLocation defaultBootstrapLocation, List<String> ignorePatterns) {
        this.userName = userName;
        this.password = password;
        this.salt = salt;
        this.cacheTtl = cacheTtl;
        this.peerDiscoveryTimeout = peerDiscoveryTimeout;
        this.peerBootstrapTimeout = peerBootstrapTimeout;
        this.shutdownAnnounceTimeout = shutdownAnnounceTimeout;
        this.defaultPort = defaultPort;
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
        this.defaultBootstrapLocation = defaultBootstrapLocation;
        this.ignorePatterns = ignorePatterns;
    }

    /**
     * Get the name of the user
     *
     * @return The username
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Set the name of the user
     *
     * @param userName The name of the user
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Get the password of the user
     *
     * @return The password of the user
     */
    public String getPassword() {
        return password;
    }

    /**
     * Set the password of the user
     *
     * @param password The password of the user
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Get the salt of the user
     *
     * @return The salt of the user
     */
    public String getSalt() {
        return salt;
    }

    /**
     * Set the salt of the user
     *
     * @param salt The salt of the user
     */
    public void setSalt(String salt) {
        this.salt = salt;
    }

    /**
     * Return the livetime of cache values (in milliseconds)
     *
     * @return The livetime of cache values
     */
    public long getCacheTtl() {
        return cacheTtl;
    }

    /**
     * Set the livetime of cache values (in milliseconds)
     *
     * @param cacheTtl The livetime of cache values
     */
    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    /**
     * Get the timeout until peer discovery should be successful (in milliseconds)
     *
     * @return The timeout until peer discovery should be successful
     */
    public long getPeerDiscoveryTimeout() {
        return peerDiscoveryTimeout;
    }

    /**
     * Set the timeout until peer discovery should be successful (in milliseconds)
     *
     * @param peerDiscoveryTimeout The timeout until peer discovery should be successful
     */
    public void setPeerDiscoveryTimeout(long peerDiscoveryTimeout) {
        this.peerDiscoveryTimeout = peerDiscoveryTimeout;
    }

    /**
     * Get the timeout until bootstrapping to a client should be succeeded (in milliseconds)
     *
     * @return The timeout until bootstrapping is succeeded
     */
    public long getPeerBootstrapTimeout() {
        return peerBootstrapTimeout;
    }

    /**
     * Set the timeout until bootstrapping to a client should be succeeded (in milliseconds)
     *
     * @param peerBootstrapTimeout The timeout until bootstrapping is succeeded
     */
    public void setPeerBootstrapTimeout(long peerBootstrapTimeout) {
        this.peerBootstrapTimeout = peerBootstrapTimeout;
    }

    /**
     * Get the timeout until a friendly shutdown should have been completed
     *
     * @return The timeout until a friendly shutdown should have been completed
     */
    public long getShutdownAnnounceTimeout() {
        return shutdownAnnounceTimeout;
    }

    /**
     * Set the timeout until a friendly shutdown should have been completed
     *
     * @param shutdownAnnounceTimeout The timeout until a friendly shutdown should have been completed
     */
    public void setShutdownAnnounceTimeout(long shutdownAnnounceTimeout) {
        this.shutdownAnnounceTimeout = shutdownAnnounceTimeout;
    }

    /**
     * Get the default port
     *
     * @return The default port
     */
    public int getDefaultPort() {
        return defaultPort;
    }

    /**
     * Set the default port
     *
     * @param defaultPort The default port
     */
    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    /**
     * Get the path to the public key of the user
     *
     * @return The path to the public key
     */
    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    /**
     * Set the public key path of the user
     *
     * @param publicKeyPath The path to the public key of the user
     */
    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    /**
     * Get the private key path
     *
     * @return The path to the private key of the user
     */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /**
     * Set the private key path
     *
     * @param privateKeyPath The path to the private key of the user
     */
    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    /**
     * Get the default bootstrap location to which the client
     * should bootstrap. May be null.
     *
     * @return The default bootstrap location
     */
    public RemoteClientLocation getDefaultBootstrapLocation() {
        return defaultBootstrapLocation;
    }

    /**
     * Set the default bootstrap location to which the client
     * should connect to on startup
     *
     * @param defaultBootstrapLocation The default bootstrap location
     */
    public void setDefaultBootstrapLocation(RemoteClientLocation defaultBootstrapLocation) {
        this.defaultBootstrapLocation = defaultBootstrapLocation;
    }

    /**
     * Get the list of ignored file glob patterns.
     * See <a href="http://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-">here</a>
     * for more information
     *
     * @return The list of file glob patterns
     */
    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    /**
     * Set the list of ignored file glob patterns.
     * See <a href="http://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-">here</a>
     * for more information
     *
     * @param ignorePatterns The list of patterns
     */
    public void setIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    /**
     * Creates a JSON representation of this application config
     *
     * @return The JSON representation
     */
    public String toJson() {
        return gson.toJson(this, ApplicationConfig.class);
    }

    /**
     * Creates an app config from its JSON representation
     *
     * @param json The json string
     *
     * @return The config created of it
     */
    public static ApplicationConfig fromJson(String json) {
        return gson.fromJson(json, ApplicationConfig.class);
    }
}
