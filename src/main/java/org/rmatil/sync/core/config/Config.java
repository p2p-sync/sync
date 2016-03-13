package org.rmatil.sync.core.config;

public enum Config {
    DEFAULT(".sync", "config", 0L, 20000L, 20000L, 5000L, 4003, "index.json", "object", "sharedWithOthers (read-only)", "sharedWithOthers (read-write)");

    private String osFolderName;

    private String configFileName;

    private long cacheTtl;

    private long peerDiscoveryTimeout;

    private long peerBootstrapTimeout;

    private long shutdownAnnounceTimeout;

    private int defaultPort;

    private String osIndexName;

    private String osObjectFolderName;

    private String sharedWithOthersReadOnlyFolderName;

    private String sharedWithOthersReadWriteFolderName;

    Config(String osFolderName, String configFileName, long cacheTtl, long peerDiscoveryTimeout, long peerBootstrapTimeout, long shutdownAnnounceTimeout, int defaultPort, String osIndexName, String osObjectFolderName, String sharedWithOthersReadOnlyFolderName, String sharedWithOthersReadWriteFolderName) {
        this.osFolderName = osFolderName;
        this.configFileName = configFileName;
        this.cacheTtl = cacheTtl;
        this.peerDiscoveryTimeout = peerDiscoveryTimeout;
        this.peerBootstrapTimeout = peerBootstrapTimeout;
        this.shutdownAnnounceTimeout = shutdownAnnounceTimeout;
        this.defaultPort = defaultPort;
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

    public long getCacheTtl() {
        return cacheTtl;
    }

    public long getPeerDiscoveryTimeout() {
        return peerDiscoveryTimeout;
    }

    public long getPeerBootstrapTimeout() {
        return peerBootstrapTimeout;
    }

    public long getShutdownAnnounceTimeout() {
        return shutdownAnnounceTimeout;
    }

    public int getDefaultPort() {
        return defaultPort;
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
