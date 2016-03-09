package org.rmatil.sync.core;

import net.engio.mbassy.bus.MBassador;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.persistence.api.IFileMetaInfo;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IObjectStore;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * A utility class to handle conflict files
 */
public class ConflictHandler {

    private static final Logger logger = LoggerFactory.getLogger(ConflictHandler.class);

    /**
     * Creates a conflict file for the given path element.
     * Note, that an ignore event for the move of the local conflict file is emitted to the event bus.
     * Additionally, a create event for the conflict file is emitted to the event bus.
     *
     * @param globalEventBus      The global event bus to publish the new conflict file event
     * @param conflictFilePostfix The postfix to append to the filename (must be unique over all clients)
     * @param objectStore         The object store to modify when creating the conflict file
     * @param storageAdapter      The storage adapter to access the path element
     * @param pathElement         The path element for which to create a conflict file
     */
    public static Path createConflictFile(MBassador<IBusEvent> globalEventBus, String conflictFilePostfix, IObjectStore objectStore, ITreeStorageAdapter storageAdapter, TreePathElement pathElement) {
        logger.info("Creating conflict file for file " + pathElement.getPath());
        PathObject pathObject;
        try {
            pathObject = objectStore.getObjectManager().getObjectForPath(pathElement.getPath());
        } catch (InputOutputException e) {
            logger.error("Failed to check file versions of file " + pathElement.getPath() + ". Message: " + e.getMessage() + ". Indicating that a conflict happened");
            return null;
        }

        // compare local and remote file versions
        List<Version> localFileVersions = pathObject.getVersions();
        Version lastLocalFileVersion = localFileVersions.size() > 0 ? localFileVersions.get(localFileVersions.size() - 1) : null;
        String lastLocalFileVersionHash = (null != lastLocalFileVersion) ? lastLocalFileVersion.getHash() : null;

        Path conflictFilePath;
        try {
            IFileMetaInfo fileMetaInfo = storageAdapter.getMetaInformation(pathElement);
            conflictFilePath = Paths.get(Naming.getConflictFileName(pathElement.getPath(), true, fileMetaInfo.getFileExtension(), conflictFilePostfix));
            globalEventBus.publish(new IgnoreBusEvent(
                    new MoveEvent(
                            Paths.get(pathElement.getPath()),
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));
            globalEventBus.publish(new CreateBusEvent(
                    new CreateEvent(
                            conflictFilePath,
                            conflictFilePath.getFileName().toString(),
                            lastLocalFileVersionHash,
                            System.currentTimeMillis()
                    )
            ));

        } catch (InputOutputException e) {
            logger.error("Can not read meta information for file " + pathElement.getPath() + ". Moving the conflict file failed");
            return null;
        }

        try {
            storageAdapter.move(StorageType.FILE, pathElement, new TreePathElement(conflictFilePath.toString()));
        } catch (InputOutputException e) {
            logger.error("Can not move conflict file " + pathElement.getPath() + " to " + conflictFilePath.toString() + ". Message: " + e.getMessage());
        }

        return conflictFilePath;
    }
}
