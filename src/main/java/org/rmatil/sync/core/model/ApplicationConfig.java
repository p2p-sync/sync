package org.rmatil.sync.core.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class ApplicationConfig {

    protected static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    protected String               userName;
    protected String               password;
    protected String               salt;
    protected long                 cacheTtl;
    protected long                 peerDiscoveryTimeout;
    protected long                 peerBootstrapTimeout;
    protected long                 shutdownAnnounceTimeout;
    protected int                  defaultPort;
    protected String               publicKeyPath;
    protected String               privateKeyPath;
    protected RemoteClientLocation defaultBootstrapLocation;
    protected List<String>         ignorePatterns;

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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public long getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public long getPeerDiscoveryTimeout() {
        return peerDiscoveryTimeout;
    }

    public void setPeerDiscoveryTimeout(long peerDiscoveryTimeout) {
        this.peerDiscoveryTimeout = peerDiscoveryTimeout;
    }

    public long getPeerBootstrapTimeout() {
        return peerBootstrapTimeout;
    }

    public void setPeerBootstrapTimeout(long peerBootstrapTimeout) {
        this.peerBootstrapTimeout = peerBootstrapTimeout;
    }

    public long getShutdownAnnounceTimeout() {
        return shutdownAnnounceTimeout;
    }

    public void setShutdownAnnounceTimeout(long shutdownAnnounceTimeout) {
        this.shutdownAnnounceTimeout = shutdownAnnounceTimeout;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public RemoteClientLocation getDefaultBootstrapLocation() {
        return defaultBootstrapLocation;
    }

    public void setDefaultBootstrapLocation(RemoteClientLocation defaultBootstrapLocation) {
        this.defaultBootstrapLocation = defaultBootstrapLocation;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

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
