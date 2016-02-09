package org.rmatil.sync.core.config;

public enum Config {
    DEFAULT(".sync", "~/.syncconfig", "config", "rsa_id.pub", "rsa_id", "index.json", "object", "sharedWithOthers (read-only)", "sharedWithOthers (read-write)");

    private String osFolderName;

    private String configFolderPath;

    private String configFileName;

    private String publicKeyFileName;

    private String privateKeyFileName;

    private String osIndexName;

    private String osObjectFolderName;

    private String sharedWithOthersReadOnlyFolderName;

    private String sharedWithOthersReadWriteFolderName;

    Config(String osFolderName, String configFolderPath, String configFileName, String publicKeyFileName, String privateKeyFileName, String osIndexName, String osObjectFolderName, String sharedWithOthersReadOnlyFolderName, String sharedWithOthersReadWriteFolderName) {
        this.osFolderName = osFolderName;
        this.configFolderPath = configFolderPath;
        this.configFileName = configFileName;
        this.publicKeyFileName = publicKeyFileName;
        this.privateKeyFileName = privateKeyFileName;
        this.osIndexName = osIndexName;
        this.osObjectFolderName = osObjectFolderName;
        this.sharedWithOthersReadOnlyFolderName = sharedWithOthersReadOnlyFolderName;
        this.sharedWithOthersReadWriteFolderName = sharedWithOthersReadWriteFolderName;
    }

    public String getOsFolderName() {
        return osFolderName;
    }

    public String getConfigFileName() {
        return configFileName;
    }

    public String getConfigFolderPath() {
        return configFolderPath;
    }

    public String getPublicKeyFileName() {
        return publicKeyFileName;
    }

    public String getPrivateKeyFileName() {
        return privateKeyFileName;
    }

    public String getOsIndexName() {
        return osIndexName;
    }

    public String getOsObjectFolderName() {
        return osObjectFolderName;
    }

    public String getSharedWithOthersReadOnlyFolderName() {
        return sharedWithOthersReadOnlyFolderName;
    }

    public String getSharedWithOthersReadWriteFolderName() {
        return sharedWithOthersReadWriteFolderName;
    }
}
