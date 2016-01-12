package org.rmatil.sync.core.messaging.fileexchange.move;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.init.client.ILocalStateRequestCallback;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.DeleteEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IRequest;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.network.core.model.ClientLocation;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.Index;
import org.rmatil.sync.version.core.model.PathObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class FileMoveRequestHandler implements ILocalStateRequestCallback {

    private static final Logger logger = LoggerFactory.getLogger(FileMoveRequestHandler.class);

    protected IStorageAdapter      storageAdapter;
    protected IObjectStore         objectStore;
    protected IClient              client;
    protected FileMoveRequest      request;
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
        if (! (iRequest instanceof FileMoveRequest)) {
            throw new IllegalArgumentException("Got request " + iRequest.getClass().getName() + " but expected " + FileMoveRequest.class.getName());
        }

        this.request = (FileMoveRequest) iRequest;
    }

    @Override
    public void run() {
        try {
            logger.info("Moving path from " + this.request.getOldPath() + " to " + this.request.getNewPath());

            IPathElement oldPathElement = new LocalPathElement(this.request.getOldPath());
            IPathElement newPathElement = new LocalPathElement(this.request.getNewPath());

            StorageType storageType = this.request.isFile() ? StorageType.FILE : StorageType.DIRECTORY;

            try {
                if (this.storageAdapter.exists(storageType, oldPathElement)) {
                    this.move(storageType, oldPathElement, newPathElement);
                } else {
                    // TODO: request file on the new path
                }
            } catch (InputOutputException e) {
                logger.error("Could not move path " + this.request.getOldPath() + " to " + this.request.getNewPath() + ". Message: " + e.getMessage());
            }

            this.client.sendDirect(this.request.getClientDevice().getPeerAddress(), new FileMoveResponse(
                    this.request.getExchangeId(),
                    new ClientDevice(this.client.getUser().getUserName(), this.client.getClientDeviceId(), this.client.getPeerAddress()),
                    new ClientLocation(this.request.getClientDevice().getClientDeviceId(), this.request.getClientDevice().getPeerAddress()),
                    true
            ));
        } catch (Exception e) {
            logger.error("Error in FileMoveRequestHandler thread for exchangeId " + this.request.getExchangeId() + ". Message: " + e.getMessage(), e);
        }
    }

    protected void move(StorageType storageType, IPathElement oldPath, IPathElement newPath)
            throws InputOutputException {

        if (StorageType.DIRECTORY == storageType) {
//            if (! this.storageAdapter.exists(StorageType.DIRECTORY, newPath)) {
////                this.storageAdapter.persist(StorageType.DIRECTORY, newPath, null);
//                // ignore the move event for this
//                // since the persisting and the later removing will be aggregated to a move
//                this.globalEventBus.publish(new IgnoreBusEvent(
//                        new MoveEvent(
//                                Paths.get(oldPath.getPath()),
//                                Paths.get(newPath.getPath()),
//                                Paths.get(newPath.getPath()).getFileName().toString(),
//                                "weIgnoreTheHash",
//                                System.currentTimeMillis()
//                        )
//                ));
//            }

//            List<IPathElement> pathElements = this.storageAdapter.getDirectoryContents(oldPath);
//
//            for (IPathElement pathElement : pathElements) {
//                StorageType childStorageType = this.storageAdapter.isFile(pathElement) ? StorageType.FILE : StorageType.DIRECTORY;
//
//                Path childPath = Paths.get(newPath.getPath()).resolve(Paths.get(oldPath.getPath()).relativize(Paths.get(pathElement.getPath())));
//                this.move(childStorageType, pathElement, new LocalPathElement(childPath.toString()));
//            }
//
//            this.storageAdapter.delete(oldPath);

            try (Stream<Path> paths = Files.walk(this.storageAdapter.getRootDir().resolve(oldPath.getPath()))) {
                paths.forEach((entry) -> {
                    this.globalEventBus.publish(new IgnoreBusEvent(
                            new MoveEvent(
                                    this.storageAdapter.getRootDir().relativize(entry),
                                    Paths.get(newPath.getPath()).resolve(Paths.get(oldPath.getPath()).relativize(this.storageAdapter.getRootDir().relativize(Paths.get(entry.toString())))),
                                    Paths.get(oldPath.getPath()).getFileName().toString(),
                                    "weIgnoreTheHash",
                                    System.currentTimeMillis()
                            )
                    ));
                });
            } catch (IOException e) {
                logger.error("Could not create ignore events for the move of " + oldPath.getPath() + ". Message: " + e.getMessage());
            }


            this.storageAdapter.move(storageType, oldPath, newPath);
        } else {
            Index idx = this.objectStore.getObjectManager().getIndex();
            String hashToObject = idx.getPaths().get(oldPath.getPath());

            PathObject pathObject = this.objectStore.getObjectManager().getObject(hashToObject);
            this.globalEventBus.publish(new IgnoreBusEvent(
                    new MoveEvent(
                            Paths.get(oldPath.getPath()),
                            Paths.get(newPath.getPath()),
                            Paths.get(newPath.getPath()).getFileName().toString(),
                            pathObject.getVersions().get(Math.max(pathObject.getVersions().size() - 1, 0)).getHash(),
                            System.currentTimeMillis()
                    )
            ));

            this.storageAdapter.move(StorageType.FILE, oldPath, new LocalPathElement(newPath.getPath()));
            // since the history move aggregator gets notified only about the deletion of the old path
            // and the creation of the new one combined with the fact, that the object store already has
            // moved its objects, he is unable to create a move event. Therefore, we ignore the create and
            // the delete event for the file
//            this.globalEventBus.publish(new IgnoreBusEvent(
//                    new DeleteEvent(
//                            Paths.get(oldPath.getPath()),
//                            Paths.get(oldPath.getPath()).getFileName().toString(),
//                            "weIgnoreTheHash",
//                            System.currentTimeMillis()
//                    )
//            ));
//            this.globalEventBus.publish(new IgnoreBusEvent(
//                    new CreateEvent(
//                            Paths.get(newPath.getPath()),
//                            Paths.get(newPath.getPath()).getFileName().toString(),
//                            "weIgnoreTheHash",
//                            System.currentTimeMillis()
//                    )
//            ));
        }
    }
}
