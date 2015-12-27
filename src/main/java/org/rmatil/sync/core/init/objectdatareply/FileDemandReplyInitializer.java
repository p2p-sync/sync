package org.rmatil.sync.core.init.objectdatareply;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.core.local.LocalStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.nio.file.Path;

/**
 * The initializer for FileDemandRequestHandler
 *
 * @see FileDemandRequestHandler
 */
public class FileDemandReplyInitializer implements IInitializer<FileDemandRequestHandler> {

    protected ClientDevice clientDevice;
    protected IObjectStore objectStore;
    protected IStorageAdapter storageAdapter;
    protected int chunkSize;

    protected FileDemandRequestHandler fileDemandRequestHandler;

    public FileDemandReplyInitializer(ClientDevice clientDevice, IObjectStore objectStore, Path rootPath, int chunkSize) {
        this.clientDevice = clientDevice;
        this.objectStore = objectStore;
        this.storageAdapter = new LocalStorageAdapter(rootPath);
        this.chunkSize = chunkSize;
    }

    @Override
    public FileDemandRequestHandler init()
            throws InitializationException {

        this.fileDemandRequestHandler = new FileDemandRequestHandler(
                this.clientDevice,
                this.objectStore,
                this.storageAdapter,
                this.chunkSize
        );

        return this.fileDemandRequestHandler;
    }

    @Override
    public void start()
            throws InitializationStartException {
        // nothing to do here
    }

    @Override
    public void stop()
            throws InitializationStopException {
        // nothing to do here
    }
}
