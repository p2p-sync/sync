package org.rmatil.sync.core.init.objecstore;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;

import java.nio.file.Paths;

public class ObjectStoreInitializer implements IInitializer<IObjectStore> {

    protected String syncFolderName;
    protected String indexFileName;
    protected String objectFolderName;

    protected ITreeStorageAdapter synchronisedFolderStorageAdapter;
    protected ITreeStorageAdapter syncFolderStorageAdapter;
    protected IObjectStore        objectStore;

    public ObjectStoreInitializer(ITreeStorageAdapter treeStorageAdapterh, String syncFolderName, String indexFileName, String objectFolderName) {
        this.synchronisedFolderStorageAdapter = treeStorageAdapterh;
        this.syncFolderName = syncFolderName;
        this.indexFileName = indexFileName;
        this.objectFolderName = objectFolderName;
        // create dedicated storage adapter for sync folder
        this.syncFolderStorageAdapter = new LocalStorageAdapter(
                Paths.get(this.synchronisedFolderStorageAdapter.getRootDir().getPath()).resolve(this.syncFolderName)
        );
    }

    @Override
    public IObjectStore init()
            throws InitializationException {

        try {
            this.objectStore = new ObjectStore(
                    this.synchronisedFolderStorageAdapter,
                    this.indexFileName,
                    this.objectFolderName,
                    this.syncFolderStorageAdapter
            );
        } catch (InputOutputException e) {
            throw new InitializationException(e);
        }

        return this.objectStore;
    }

    @Override
    public void start()
            throws InitializationStartException {
        // sync object store with contents in the root path
        try {
            this.objectStore.sync();
        } catch (InputOutputException e) {
            throw new InitializationStartException(e);
        }
    }

    @Override
    public void stop() {
        // nothing to do here
    }
}
