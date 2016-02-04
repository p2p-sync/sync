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
     * The root dir for the 3rd client
     */
    protected String testRootDir3 = "./org.rmatil.sync.test.dir3";

    /**
     * The root dir for the 4th client
     */
    protected String testRootDir4 = "./org.rmatil.sync.test.dir4";

    /**
     * The username of the user
     */
    protected String username = "Quiche Hollandaise";

    /**
     * The username of the user
     */
    protected String username2 = "Quiche Bolognese";

    /**
     * The password for test purposes
     */
    protected String password2 = "ThisIsNotSafeChangeIt";

    /**
     * The password for test purposes
     */
    protected String password = "ThisIsAlsoNotSafe...";

    /**
     * The salt to use
     */
    protected String salt = "SaltAndPepperMakesTheMealBetter";

    /**
     * The salt to use
     */
    protected String salt2 = "SaltAndPepperMakesTheMealBetter.Haha";

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

    /**
     * The port for the client 3
     */
    protected int port3 = 4005;

    /**
     * The port for the client 4
     */
    protected int port4 = 4006;

    public String getTestRootDir1() {
        return testRootDir1;
    }

    public String getTestRootDir2() {
        return testRootDir2;
    }

    public String getTestRootDir3() {
        return testRootDir3;
    }

    public String getTestRootDir4() {
        return testRootDir4;
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

    public String getUsername2() {
        return username2;
    }

    public String getPassword2() {
        return password2;
    }

    public String getSalt2() {
        return salt2;
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

    public int getPort3() {
        return port3;
    }

    public int getPort4() {
        return port4;
    }
}
