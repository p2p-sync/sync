package org.rmatil.sync.core.config;

public enum Config {
    DEFAULT(".sync", "config", "index.json", "object", "sharedWithOthers (read-only)", "sharedWithOthers (read-write)");

    private String osFolderName;

    private String configFileName;

    private String osIndexName;

    private String osObjectFolderName;

    private String sharedWithOthersReadOnlyFolderName;

    private String sharedWithOthersReadWriteFolderName;

    Config(String osFolderName, String configFileName, String osIndexName, String osObjectFolderName, String sharedWithOthersReadOnlyFolderName, String sharedWithOthersReadWriteFolderName) {
        this.osFolderName = osFolderName;
        this.configFileName = configFileName;
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
