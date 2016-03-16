package org.rmatil.sync.core.init.objecstore;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.local.LocalStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.ObjectStore;

import java.nio.file.Path;

public class ObjectStoreInitializer implements IInitializer<IObjectStore> {

    protected Path   rootPath;
    protected String syncFolderName;
    protected String indexFileName;
    protected String objectFolderName;

    protected ITreeStorageAdapter synchronisedFolderStorageAdapter;
    protected ITreeStorageAdapter syncFolderStorageAdapter;
    protected IObjectStore        objectStore;

    public ObjectStoreInitializer(Path rootPath, String syncFolderName, String indexFileName, String objectFolderName) {
        this.rootPath = rootPath;
        this.syncFolderName = syncFolderName;
        this.indexFileName = indexFileName;
        this.objectFolderName = objectFolderName;
    }

    @Override
    public IObjectStore init()
            throws InitializationException {

        this.synchronisedFolderStorageAdapter = new LocalStorageAdapter(this.rootPath);
        this.syncFolderStorageAdapter = new LocalStorageAdapter(this.rootPath.resolve(this.syncFolderName));


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
