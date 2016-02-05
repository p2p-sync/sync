package org.rmatil.sync.core.init;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.rmatil.sync.core.model.RemoteClientLocation;

import java.security.PrivateKey;
import java.security.PublicKey;

public class ApplicationConfig {

    protected static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    protected String userName;
    protected String password;
    protected String salt;
    protected int defaultPort;
    protected PublicKey publicKey;
    protected PrivateKey privateKey;
    protected RemoteClientLocation defaultBootstrapLocation;

    public ApplicationConfig(String userName, String password, String salt, int defaultPort, PublicKey publicKey, PrivateKey privateKey, RemoteClientLocation defaultBootstrapLocation) {
        this.userName = userName;
        this.password = password;
        this.salt = salt;
        this.defaultPort = defaultPort;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.defaultBootstrapLocation = defaultBootstrapLocation;
    }

    public String getUserName() {
        return userName;
    }

    public String getPassword() {
        return password;
    }

    public String getSalt() {
        return salt;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public RemoteClientLocation getDefaultBootstrapLocation() {
        return defaultBootstrapLocation;
    }

    /**
     * Creates a JSON representation of this application config
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
