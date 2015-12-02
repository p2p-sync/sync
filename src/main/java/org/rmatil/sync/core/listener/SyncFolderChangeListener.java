package org.rmatil.sync.core.listener;

import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.commons.hashing.HashingAlgorithm;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.event.aggregator.api.IEventListener;
import org.rmatil.sync.event.aggregator.core.events.*;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.version.api.IVersionManager;
import org.rmatil.sync.version.api.PathType;
import org.rmatil.sync.version.core.model.PathObject;
import org.rmatil.sync.version.core.model.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SyncFolderChangeListener implements IEventListener {

    final static Logger logger = LoggerFactory.getLogger(SyncFolderChangeListener.class);

    protected IVersionManager versionManager;

    protected Path rootDir;

    public SyncFolderChangeListener(Path rootDir, IVersionManager versionManager) {
        this.versionManager = versionManager;
        this.rootDir = rootDir;
    }

    public void onChange(List<IEvent> list) {
        logger.trace("Got notified about " + list.size() + " new events");

        for (IEvent event : list) {
            switch (event.getEventName()) {
                case ModifyEvent.EVENT_NAME:
                    this.addVersion(event);
                    break;
                case CreateEvent.EVENT_NAME:
                    this.createVersion(event);
                    break;
                case DeleteEvent.EVENT_NAME:
                    this.removeObjectPath(event);
                    break;
                case MoveEvent.EVENT_NAME:
                    this.moveVersion(event);
                    break;
            }
        }
    }

    protected void createVersion(IEvent event) {
        logger.trace("Creating path object for file " + event.getPath().toString());
        // filename.txt
        // myPath/to/filename.txt
        int idx = event.getPath().toString().indexOf(event.getName());
        String pathToFileWithoutFilename = event.getPath().toString().substring(0, idx);

        logger.trace("Path to file " + event.getName() + " is " + pathToFileWithoutFilename);

        boolean fileExists = this.rootDir.resolve(event.getPath()).toFile().exists();

        Path absolutePathOnFilesystem = this.rootDir.resolve(event.getPath());

        PathType pathType = null;
        if (absolutePathOnFilesystem.toFile().isDirectory()) {
            pathType = PathType.DIRECTORY;
        } else if (absolutePathOnFilesystem.toFile().isFile()) {
            pathType = PathType.FILE;
        }

        logger.trace("Path type is: " + pathType);

        PathObject pathObject = new PathObject(event.getName(), pathToFileWithoutFilename, pathType, false, new ArrayList<>(), new ArrayList<>());
        try {
            logger.trace("Writing path object...");
            this.versionManager.getObjectManager().writeObject(pathObject);
        } catch (InputOutputException e) {
            logger.error("Could not write new object path: " + e.getMessage());
        }

        this.addVersion(event);
    }

    protected void addVersion(IEvent event) {
        Version v = new Version(event.getHash());

        try {
            logger.trace("Adding version for file " + event.getPath().toString());
            this.versionManager.addVersion(v, event.getPath().toString());
        } catch (InputOutputException e) {
            logger.error("Could not add version: " + e.getMessage());
        }
    }

    protected void removeObjectPath(IEvent event) {
        try {
            logger.trace("Removing object for file " + event.getPath().toString());
            this.versionManager.getObjectManager().removeObject(Hash.hash(HashingAlgorithm.SHA_256, event.getPath().toString()));
        } catch (InputOutputException e) {
            logger.error("Could not remove object for file " + event.getPath() + " from object store: " + e.getMessage());
        }
    }

    protected void moveVersion(IEvent event) {
        MoveEvent moveEvent = (MoveEvent) event;

        try {
            logger.trace("Moving object for file (old: " + moveEvent.getPath().toString() + ", new: " + moveEvent.getNewPath().toString() + ")");
            PathObject oldObject = this.versionManager.getObjectManager().getObject(Hash.hash(HashingAlgorithm.SHA_256, moveEvent.getPath().toString()));
            PathObject newObject = new PathObject(
                    oldObject.getName(),
                    Naming.getPathWithoutFileName(event.getName(), ((MoveEvent) event).getNewPath().toString()),
                    oldObject.getPathType(),
                    oldObject.isShared(),
                    oldObject.getSharers(),
                    oldObject.getVersions()
            );
            this.versionManager.getObjectManager().writeObject(newObject);
            this.versionManager.getObjectManager().removeObject(Hash.hash(HashingAlgorithm.SHA_256, moveEvent.getPath().toString()));
        } catch (InputOutputException e) {
            logger.error("Could not move object for file " + moveEvent.getPath() + " in object store: " + e.getMessage());
        }
    }
}
