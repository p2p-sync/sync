package org.rmatil.sync.core.messaging.fileexchange.delete;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.core.messaging.fileexchange.move.FileMoveResponse;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileDeleteRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileDeleteExchangeHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected FileDeleteRequest    request;
    protected MBassador<IBusEvent> globalEventBus;

    @Override
    public void setStorageAdapter(IStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
    }

    @Override
    public void setObjectStore(IObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public void setGlobalEventBus(MBassador<IBusEvent> globalEventBus) {
        this.globalEventBus = globalEventBus;
    }

    @Override
    public void setClient(IClient iClient) {
        this.client = iClient;
    }

    @Override
    public void setRequest(IRequest iRequest) {
        if (! (iRequest instanceof FileDeleteRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileDeleteExchangeHandler.class.getName());
        }

        this.request = (FileDeleteRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Deleting path on " + this.request.getPathToDelete());

            IPathElement pathToDelete = new LocalPathElement(this.request.getPathToDelete());
            try {
                if (this.storageAdapter.exists(StorageType.DIRECTORY, pathToDelete) || this.storageAdapter.exists(StorageType.FILE, pathToDelete)) {
                    // create ignore events for all dir contents
                    try (Stream<Path> paths = Files.walk(this.storageAdapter.getRootDir().resolve(pathToDelete.getPath()))) {
                        paths.forEach((entry) -> {
                            this.globalEventBus.publish(new IgnoreBusEvent(
                                    new DeleteEvent(
                                            Paths.get(pathToDelete.getPath()),
                                            Paths.get(pathToDelete.getPath()).getFileName().toString(),
                                            "weIgnoreTheHash",
                                            System.currentTimeMillis()
                                    )
                            ));
                        });
                    } catch (IOException e) {
                        logger.error("Could not create ignore events for the deletion of " + this.request.getPathToDelete() + ". Message: " + e.getMessage());
                    }

                    this.storageAdapter.delete(pathToDelete);
                }
            } catch (InputOutputException e) {
                logger.error("Could not delete path " + pathToDelete.getPath() + ". Message: " + e.getMessage());
            }

            this.client.sendDirect(this.request.getClientDevice().getPeerAddress(), new FileDeleteResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    true
            ));

        } catch (Exception e) {
            logger.error("Error in FileDeleteRequestHandler thread. Message: " + e.getMessage(), e);
        }
    }
}
