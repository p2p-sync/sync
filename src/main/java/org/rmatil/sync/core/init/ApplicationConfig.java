package org.rmatil.sync.core.init;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.rmatil.sync.core.model.RemoteClientLocation;

public class ApplicationConfig {

    protected static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    protected String               userName;
    protected String               password;
    protected String               salt;
    protected int                  defaultPort;
    protected String               publicKeyPath;
    protected String               privateKeyPath;
    protected RemoteClientLocation defaultBootstrapLocation;

    public ApplicationConfig(String userName, String password, String salt, int defaultPort, String publicKeyPath, String privateKeyPath, RemoteClientLocation defaultBootstrapLocation) {
        this.userName = userName;
        this.password = password;
        this.salt = salt;
        this.defaultPort = defaultPort;
        this.publicKeyPath = publicKeyPath;
        this.privateKeyPath = privateKeyPath;
        this.defaultBootstrapLocation = defaultBootstrapLocation;
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

    /**
     * Creates a JSON representation of this application config
     *
     * @return
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
