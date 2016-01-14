package org.rmatil.sync.test.config;

public enum Config {

    DEFAULT();

    /**
     * The root dir for the first client
     */
    protected String testRootDir1 = "./org.rmatil.sync.test.dir1";

    /**
     * The root dir for the second client
     */
    protected String testRootDir2 = "./org.rmatil.sync.test.dir2";

    /**
     * The username of the user
     */
    protected String username = "Quiche Hollandaise";

    /**
     * The password for test purposes
     */
    protected String password = "ThisIsNotSafeChangeIt";

    /**
     * The salt to use
     */
    protected String salt = "SaltAndPepperMakesTheMealBetter";

    /**
     * The folder name for the settings folder
     */
    protected String syncFolderName = ".sync";

    /**
     * The file name for the object store's index
     */
    protected String indexFileName = "index.json";

    /**
     * The directory name for the object store's objects
     */
    protected String objectFolderName = "object";

    /**
     * The port for the client 1
     */
    protected int port1 = 4003;

    /**
     * The port for the client 2
     */
    protected int port2 = 4004;

    public String getTestRootDir1() {
        return testRootDir1;
    }

    public String getTestRootDir2() {
        return testRootDir2;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getSalt() {
        return salt;
    }

    public String getSyncFolderName() {
        return syncFolderName;
    }

    public String getIndexFileName() {
        return indexFileName;
    }

    public String getObjectFolderName() {
        return objectFolderName;
    }

    public int getPort1() {
        return port1;
    }

    public int getPort2() {
        return port2;
    }
}
