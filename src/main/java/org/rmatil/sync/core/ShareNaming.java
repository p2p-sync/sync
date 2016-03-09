package org.rmatil.sync.core;

import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.AccessType;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Sharer;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ShareNaming {


    /**
     * Relativizes the given path to its most upper parent which is also shared with the given sharer.
     * Example:
     * Having a given path <i>syncedFolder/aDir/bDir/cDir/myFile.txt</i>, and a most upper shared directory of the
     * given sharer at <i>syncedFolder/aDir/bDir</i>, then this method will return <i>cDir/myFile.txt</i>,
     * so that the file can be placed in the sharers directory at the correct path.
     *
     * @param storageAdapter   The storage adapter to access the synchronised folder
     * @param objectStore      The object store to get path objects of sub-paths
     * @param relativeFilePath The relative path of the file in the synced folder
     * @param sharerUsername   The sharer to check for
     * @param accessType       The access type
     *
     * @return The relativized path
     *
     * @throws InputOutputException If reading the storage adapter / object store failed
     */
    public static String getRelativePathToSharedFolderBySharer(ITreeStorageAdapter storageAdapter, IObjectStore objectStore, String relativeFilePath, String sharerUsername, AccessType accessType)
            throws InputOutputException {
        // look up if there is any direct parent directory which is also shared with the given path.
        // if so, then we "add" the given file to that directory, resolving the path relatively to that one

        Path origPath = Paths.get(relativeFilePath);
        Path path = Paths.get(relativeFilePath);

        int pathCtr = path.getNameCount() - 1;
        while (path.getNameCount() > 1) {
            Path subPath = path.subpath(0, path.getNameCount() - 1); // endIndex is exclusive
            TreePathElement subPathElement = new TreePathElement(subPath.toString());

            if (storageAdapter.exists(StorageType.DIRECTORY, subPathElement) ||
                    storageAdapter.exists(StorageType.FILE, subPathElement)) {
                // check whether the parent is shared
                PathObject parentObject = objectStore.getObjectManager().getObjectForPath(subPathElement.getPath());


                if (! parentObject.isShared()) {
                    // parent is not shared at all
                    break;
                } else {
                    // now check if there is a sharer present for the given username and access type
                    boolean sharerIsPresent = false;
                    for (Sharer sharer : parentObject.getSharers()) {
                        if (sharer.getUsername().equals(sharerUsername) && sharer.getAccessType().equals(accessType)) {
                            // ok, we found him
                            sharerIsPresent = true;
                        }
                    }

                    if (! sharerIsPresent) {
                        break;
                    }
                }

                // there is a parent which is also shared with the given user
                if (subPath.getNameCount() == 1) {
                    // we tested the most upper path, so we can break safely here
                    // -> actually prevent an IllegalArgumentException for subpath
                    pathCtr--;
                    break;
                } else {
                    path = path.subpath(0, path.getNameCount() - 1); // endIndex is exclusive
                    pathCtr--;
                }
            }
        }

        return origPath.subpath(pathCtr, origPath.getNameCount()).toString();
    }

    /**
     * Relativizes the given path to its most upper parent which is also owned by the given owner.
     * Example:
     * Having a given path <i>syncedFolder/aDir/bDir/cDir/myFile.txt</i>, and a most upper shared directory of the
     * given owner at <i>syncedFolder/aDir/bDir</i>, then this method will return <i>cDir/myFile.txt</i>,
     * so that the file can be placed in the sharers directory at the correct path.
     *
     * @param storageAdapter   The storage adapter to access the synchronised folder
     * @param objectStore      The object store to get path objects of sub-paths
     * @param relativeFilePath The relative path of the file in the synced folder
     * @param ownerUsername    The sharer to check for
     *
     * @return The relativized path
     *
     * @throws InputOutputException If reading the storage adapter / object store failed
     */
    public static String getRelativePathToSharedFolderByOwner(ITreeStorageAdapter storageAdapter, IObjectStore objectStore, String relativeFilePath, String ownerUsername)
            throws InputOutputException {
        // look up if there is any direct parent directory which is also shared with the given path.
        // if so, then we "add" the given file to that directory, resolving the path relatively to that one

        Path origPath = Paths.get(relativeFilePath);
        Path path = Paths.get(relativeFilePath);

        int pathCtr = path.getNameCount() - 1;
        while (path.getNameCount() > 1) {
            Path subPath = path.subpath(0, path.getNameCount() - 1); // endIndex is exclusive
            TreePathElement subPathElement = new TreePathElement(subPath.toString());

            boolean ownerIsPresent = false;
            if (storageAdapter.exists(StorageType.DIRECTORY, subPathElement) ||
                    storageAdapter.exists(StorageType.FILE, subPathElement)) {
                // check whether the parent is shared
                PathObject parentObject = objectStore.getObjectManager().getObjectForPath(subPathElement.getPath());

                if (ownerUsername.equals(parentObject.getOwner())) {
                    ownerIsPresent = true;
                }
            }

            if (! ownerIsPresent) {
                break;
            }

            // there is a parent which is also shared with the given user
            if (subPath.getNameCount() == 1) {
                // we tested the most upper path, so we can break safely here
                // -> actually prevent an IllegalArgumentException for subpath
                pathCtr--;
                break;
            } else {
                path = path.subpath(0, path.getNameCount() - 1); // endIndex is exclusive
                pathCtr--;
            }
        }

        return origPath.subpath(pathCtr, origPath.getNameCount()).toString();
    }

    /**
     * Returns a unique filename for the given relative path
     *
     * @param storageAdapter The storage adapter to check for existence of the given path
     * @param relativePath   The relative path to find a unique filename for
     * @param isFile         Whether the given path is a file or not
     *
     * @return The unique file name
     *
     * @throws InputOutputException If checking whether the path exists or not fails
     */
    public static String getUniqueFileName(ITreeStorageAdapter storageAdapter, String relativePath, boolean isFile)
            throws InputOutputException {
        String oldFileName = Paths.get(relativePath).getFileName().toString();
        String newFileName = oldFileName;

        StorageType storageType = isFile ? StorageType.FILE : StorageType.DIRECTORY;

        String pathToFileWithoutFileName = Naming.getPathWithoutFileName(oldFileName, relativePath);

        int ctr = 1;
        while (storageAdapter.exists(storageType, new TreePathElement(pathToFileWithoutFileName + "/" + newFileName))) {
            int firstIndexOfDot = oldFileName.indexOf(".");

            if (- 1 != firstIndexOfDot) {
                // myFile.rar.zip
                // tmpFileName := myFile
                String tmpFileName = oldFileName.substring(0, Math.max(0, firstIndexOfDot));
                // tmpFileName := myFile (1)
                tmpFileName = tmpFileName + " (" + ctr + ")";
                // tmpfileName := myFile (1).rar.zip
                tmpFileName = tmpFileName.concat(oldFileName.substring(firstIndexOfDot, oldFileName.length()));

                newFileName = tmpFileName;
            } else {
                // no dot in the filename -> just append the ctr
                newFileName = oldFileName + " (" + ctr + ")";
            }

            ctr++;
        }

        // replace the **last** occurrence of the filename
        int lastIndex = relativePath.lastIndexOf(oldFileName);

        return relativePath.substring(0, lastIndex).concat(newFileName);
    }
}
