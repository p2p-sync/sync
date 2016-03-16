package org.rmatil.sync.core;

import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandlerResult;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreResponse;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A utility class to zip and unzip object stores
 */
public class Zip {

    private static final Logger logger = LoggerFactory.getLogger(Zip.class);

    /**
     * Zips the given object store and returns the zip archive in bytes
     *
     * @param objectStore The object store to zip
     *
     * @return The zip archive in bytes
     *
     * @throws IOException          If creating the ZipOutputStream fails
     * @throws InputOutputException If reading the object store using its storageAdapter fails
     */
    public static byte[] zipObjectStore(IObjectStore objectStore)
            throws IOException, InputOutputException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ITreeStorageAdapter objectStorageAdapter = objectStore.getObjectManager().getStorageAdapater();

        List<TreePathElement> directoryContents = objectStorageAdapter.getDirectoryContents(new TreePathElement("."));

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            for (TreePathElement pathElement : directoryContents) {
                // zip entries are only detected as directories if they are ending
                // with a slash..?!?

                String path = pathElement.getPath();
                if (objectStorageAdapter.isDir(pathElement) &&
                        ! path.endsWith("/")) {
                    // now zip entry is happy
                    path = path.concat("/");
                }

                ZipEntry zipEntry = new ZipEntry(path);

                zipOutputStream.putNextEntry(zipEntry);

                if (objectStorageAdapter.isFile(pathElement)) {
                    zipOutputStream.write(objectStorageAdapter.read(pathElement));
                }

                zipOutputStream.closeEntry();
            }
        }

        byteArrayOutputStream.flush();
        byte[] zipFile = byteArrayOutputStream.toByteArray();
        byteArrayOutputStream.close();

        return zipFile;
    }

    /**
     * Unzips the given object store and creates its instance in the given object store's folder.
     * Each client has gets its own object store, which is identified by the clients id
     *
     * @param objectStore The object store of which we use its folder to extract the other object stores
     * @param result      The fetch object store result having the zipped object stores in it
     *
     * @return A map having as key the client device from which the object store was fetched and as value the created object store
     */
    public static Map<ClientDevice, IObjectStore> unzipObjectStore(IObjectStore objectStore, FetchObjectStoreExchangeHandlerResult result) {
        Map<ClientDevice, IObjectStore> objectStores = new HashMap<>();
        // we create the zip file in our .sync folder
        ITreeStorageAdapter objectStoreStorageAdapter = objectStore.getObjectManager().getStorageAdapater();

        for (FetchObjectStoreResponse response : result.getResponses()) {
            try {
                logger.trace("Creating object store directory for client " + response.getClientDevice().getClientDeviceId());
                // path is .sync/<ClientDevice>
                Path objectStorePath = Paths.get(response.getClientDevice().getClientDeviceId().toString());
                objectStoreStorageAdapter.persist(StorageType.DIRECTORY, new TreePathElement(objectStorePath.toString()), null);

                // create .sync folder in it
                objectStoreStorageAdapter.persist(StorageType.DIRECTORY, new TreePathElement(objectStorePath.resolve(".sync").toString()), null);

                // create storage adapter for object store
                ITreeStorageAdapter localStorageAdapter = new LocalStorageAdapter(
                        Paths.get(objectStoreStorageAdapter.getRootDir().getPath()).resolve(objectStorePath).resolve(".sync")
                );


                logger.trace("Extracting zip file from client " + response.getClientDevice().getClientDeviceId());

                ByteArrayInputStream inputStream = new ByteArrayInputStream(response.getObjectStore());
                ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));

                // actually unzip
                unzip(zipInputStream, localStorageAdapter);

                ObjectStore createdObjectStore = new ObjectStore(localStorageAdapter, "index.json", "object", localStorageAdapter);
                objectStores.put(response.getClientDevice(), createdObjectStore);
            } catch (InputOutputException | IOException e) {
                logger.error("Could not write object store for client " + response.getClientDevice().getClientDeviceId() + "(" + response.getClientDevice().getPeerAddress().inetAddress().getHostName() + ":" + response.getClientDevice().getPeerAddress().tcpPort() + ". Message: " + e.getMessage());
            }
        }

        return objectStores;
    }

    /**
     * Unzips the given object store in the folder of the specified object store.
     * The folder in which the object store is unzipped is named after the given objectStoreName.
     *
     * @param objectStore       The object store in which the given object store should be unzipped
     * @param objectStoreName   The folder name in which the object store is unzipped
     * @param zippedObjectStore The zipped object store to unzip
     *
     * @return The unzipped object store
     */
    public static IObjectStore unzipObjectStore(IObjectStore objectStore, String objectStoreName, byte[] zippedObjectStore) {
        // we create the zip file in our .sync folder
        ITreeStorageAdapter objectStoreStorageAdapter = objectStore.getObjectManager().getStorageAdapater();


        try {
            // path is .sync/<ClientDevice>
            Path objectStorePath = Paths.get(objectStoreName);
            logger.trace("Creating object store directory in dir " + Paths.get(objectStore.getObjectManager().getStorageAdapater().getRootDir().getPath()).resolve(objectStoreName));
            objectStoreStorageAdapter.persist(StorageType.DIRECTORY, new TreePathElement(objectStorePath.toString()), null);

            // create .sync folder in it
            objectStoreStorageAdapter.persist(StorageType.DIRECTORY, new TreePathElement(objectStorePath.resolve(".sync").toString()), null);

            // create storage adapter for object store
            ITreeStorageAdapter localStorageAdapter = new LocalStorageAdapter(
                    Paths.get(objectStoreStorageAdapter.getRootDir().getPath()).resolve(objectStorePath).resolve(".sync")
            );

            ByteArrayInputStream inputStream = new ByteArrayInputStream(zippedObjectStore);
            ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));

            // actually unzip
            unzip(zipInputStream, localStorageAdapter);

            return new ObjectStore(localStorageAdapter, "index.json", "object", localStorageAdapter);
        } catch (InputOutputException | IOException e) {
            logger.error("Could not write object store in dir " + objectStoreName + ". Message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Unzips the given zip input stream to the location of the given storage adapter
     *
     * @param zipInputStream The zip file to unzip
     * @param storageAdapter The storage adapter to write the unzipped files to
     *
     * @throws IOException          If reading or writing the zip input stream fails
     * @throws InputOutputException If reading or writing the storage adapter fails
     */
    protected static void unzip(ZipInputStream zipInputStream, ITreeStorageAdapter storageAdapter)
            throws IOException, InputOutputException {
        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            logger.trace("Extracting " + entry);

            int count;
            byte data[] = new byte[2048];

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            while ((count = zipInputStream.read(data, 0, 2048)) != - 1) {
                outputStream.write(data, 0, count);
            }

            outputStream.flush();
            outputStream.close();

            if (entry.isDirectory()) {
                // zip entries rely on an ending slash to detect directories,
                // therefore, we remove them before writing to disk
                String path = entry.getName();

                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }

                storageAdapter.persist(StorageType.DIRECTORY, new TreePathElement(path), null);
            } else {
                storageAdapter.persist(StorageType.FILE, new TreePathElement(entry.getName()), outputStream.toByteArray());
            }
        }

        zipInputStream.close();
    }
}
