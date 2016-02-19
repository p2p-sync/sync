package org.rmatil.sync.test.base;

import org.rmatil.sync.core.init.objecstore.ObjectStoreInitializer;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.config.Config;
import org.rmatil.sync.version.api.IObjectStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BaseTest {

    protected static final Path   ROOT_TEST_DIR1     = Paths.get(Config.DEFAULT.getTestRootDir1());
    protected static final Path   ROOT_TEST_DIR2     = Paths.get(Config.DEFAULT.getTestRootDir2());
    protected static final String SYNC_FOLDER_NAME   = Config.DEFAULT.getSyncFolderName();
    protected static final String INDEX_FILE_NAME    = Config.DEFAULT.getIndexFileName();
    protected static final String OBJECT_FOLDER_NAME = Config.DEFAULT.getObjectFolderName();

    /**
     * Creates, inits and starts an object store in the given root test dir
     *
     * @param rootTestDir The root directory in which to create the object store
     *
     * @return The created object store
     */
    protected static IObjectStore createObjectStore(Path rootTestDir) {
        ObjectStoreInitializer objectStoreInitializer1 = new ObjectStoreInitializer(
                rootTestDir,
                SYNC_FOLDER_NAME,
                INDEX_FILE_NAME,
                OBJECT_FOLDER_NAME
        );
        IObjectStore objectStore = objectStoreInitializer1.init();
        objectStoreInitializer1.start();

        return objectStore;
    }

    /**
     * Create the sharedWithOthers (read/write) or sharedWithOthers (read) folders
     * if the do not exist yet.
     *
     * @param rootDir The root dir in which to create the shared dirs
     *
     * @throws InputOutputException If creating the dirs failed
     */
    protected static void createSharedDirsIfNotExisting(Path rootDir)
            throws InputOutputException, IOException {
        if (! rootDir.resolve(org.rmatil.sync.core.config.Config.DEFAULT.getSharedWithOthersReadOnlyFolderName()).toFile().exists()) {
            Files.createDirectory(rootDir.resolve(org.rmatil.sync.core.config.Config.DEFAULT.getSharedWithOthersReadOnlyFolderName()));
        }

        if (! rootDir.resolve(org.rmatil.sync.core.config.Config.DEFAULT.getSharedWithOthersReadWriteFolderName()).toFile().exists()) {
            Files.createDirectory(rootDir.resolve(org.rmatil.sync.core.config.Config.DEFAULT.getSharedWithOthersReadWriteFolderName()));
        }

    }

    /**
     * Creates the test directories
     *
     * @throws IOException If creating the directories failed
     */
    protected static void createTestDirs()
            throws IOException {
        if (! ROOT_TEST_DIR1.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR1);
        }

        if (! ROOT_TEST_DIR2.toFile().exists()) {
            Files.createDirectory(ROOT_TEST_DIR2);
        }
    }

    /**
     * Creates the .sync folders
     *
     * @throws IOException If creating failed
     */
    protected static void createObjectStoreDirs()
            throws IOException {
        Path syncFolder1 = ROOT_TEST_DIR1.resolve(SYNC_FOLDER_NAME);
        Path syncFolder2 = ROOT_TEST_DIR2.resolve(SYNC_FOLDER_NAME);

        if (! syncFolder1.toFile().exists()) {
            Files.createDirectory(syncFolder1);
        }

        if (! syncFolder2.toFile().exists()) {
            Files.createDirectory(syncFolder2);
        }
    }

    /**
     * Deletes the test directories and all contents in them
     */
    protected static void deleteTestDirs() {
        if (ROOT_TEST_DIR1.toFile().exists()) {
            delete(ROOT_TEST_DIR1.toFile());
        }

        if (ROOT_TEST_DIR2.toFile().exists()) {
            delete(ROOT_TEST_DIR2.toFile());
        }
    }

    /**
     * Deletes recursively the given file (if it is a directory)
     * or just removes itself
     *
     * @param file The file or dir to remove
     *
     * @return True if the deletion was successful
     */
    protected static boolean delete(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();

            if (null != contents) {
                for (File child : contents) {
                    delete(child);
                }
            }

            file.delete();

            return true;
        } else {
            return file.delete();
        }
    }
}
