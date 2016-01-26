package org.rmatil.sync.core.config;

public enum Config {
    DEFAULT("sharedWithOthers (read-only)", "sharedWithOthers (read-write)");

    private String sharedWithOthersReadOnlyFolderName;

    private String shareWithOthersReadWriteFolderName;

    Config(String sharedWithOthersReadOnlyFolderName, String shareWithOthersReadWriteFolderName) {
        this.sharedWithOthersReadOnlyFolderName = sharedWithOthersReadOnlyFolderName;
        this.shareWithOthersReadWriteFolderName = shareWithOthersReadWriteFolderName;
    }

    public String getSharedWithOthersReadOnlyFolderName() {
        return sharedWithOthersReadOnlyFolderName;
    }

    public String getSharedWithOthersReadWriteFolderName() {
        return shareWithOthersReadWriteFolderName;
    }
}
