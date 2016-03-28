package org.rmatil.sync.test.syncer;

import org.junit.Test;
import org.rmatil.sync.core.Sync;
import org.rmatil.sync.core.api.IFileSyncer;
import org.rmatil.sync.core.api.ISharingSyncer;
import org.rmatil.sync.core.config.Config;
import org.rmatil.sync.core.init.ApplicationConfigFactory;
import org.rmatil.sync.core.init.client.LocalStateObjectDataReplyHandler;
import org.rmatil.sync.core.model.ApplicationConfig;
import org.rmatil.sync.event.aggregator.api.IEventAggregator;
import org.rmatil.sync.network.api.INode;
import org.rmatil.sync.network.core.messaging.ObjectDataReplyHandler;
import org.rmatil.sync.persistence.core.tree.ITreeStorageAdapter;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.base.BaseIT;
import org.rmatil.sync.version.api.IObjectStore;

import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * Test accessor before resp. after initialising and bootstrapping a node
 */
public class SyncIT extends BaseIT {

    @Test
    public void test()
            throws NoSuchAlgorithmException, InterruptedException, InputOutputException {

        Sync.init(STORAGE_ADAPTER_1);

        assertTrue("ObjectStoreFolder should exist", Files.exists(ROOT_TEST_DIR1.resolve(Config.DEFAULT.getOsFolderName())));
        assertTrue("ObjectFolder should exist", Files.exists(ROOT_TEST_DIR1.resolve(Config.DEFAULT.getOsFolderName()).resolve(Config.DEFAULT.getOsObjectFolderName())));
        assertTrue("SharedWithOthers (read-write) should exist", Files.exists(ROOT_TEST_DIR1.resolve(Config.DEFAULT.getSharedWithOthersReadWriteFolderName())));
        assertTrue("SharedWithOthers (read-only) should exist", Files.exists(ROOT_TEST_DIR1.resolve(Config.DEFAULT.getSharedWithOthersReadOnlyFolderName())));

        Sync sync = new Sync(STORAGE_ADAPTER_1);

        assertNull("Node should be null before connecting", sync.getNode());
        assertNull("Event Aggregator should be null before connecting", sync.getEventAggregator());
        assertNull("Background Syncer Executor Service should be null before connecting", sync.getBackgroundSyncerExecutorService());
        assertNull("File Syncer should be null before connecting", sync.getFileSyncer());
        assertNull("Sharing Syncer should be null before connecting", sync.getSharingSyncer());
        assertNull("Object Store should be null before connecting", sync.getObjectStore());
        assertEquals("Storage Adapter should be the same", STORAGE_ADAPTER_1, sync.getStorageAdapter());

        ApplicationConfig appConfig = ApplicationConfigFactory.createBootstrapApplicationConfig();

        appConfig.setUserName(USER_1.getUserName());
        appConfig.setPassword(USER_1.getPassword());
        appConfig.setSalt(USER_1.getSalt());

        sync.connect(appConfig);

        INode node = sync.getNode();
        assertNotNull("Node should not be null after connecting", node);
        assertNotNull("Client Device Id should not be null", node.getClientDeviceId());
        assertNotNull("PeerAddress should not be null", node.getPeerAddress());
        assertTrue("Node should be connected", node.isConnected());

        assertEquals("UserName should be equals", USER_1.getUserName(), node.getUser().getUserName());
        assertEquals("Salt should be equals", USER_1.getSalt(), node.getUser().getSalt());
        assertEquals("Password should be equals", USER_1.getPassword(), node.getUser().getPassword());

        assertNotNull("Node Manager should not be null", node.getNodeManager());
        assertNotNull("Identifier Manager should not be null", node.getIdentifierManager());
        assertNotNull("UserManager should not be null", node.getUserManager());

        ObjectDataReplyHandler objectDataReplyHandler = node.getObjectDataReplyHandler();
        assertNotNull("ObjectDataReplyHandler should not be null", objectDataReplyHandler);
        assertThat("ObjectDataReplyHandler should be an instance of LocalStateObjectDataReplyHandler", objectDataReplyHandler, is(instanceOf(LocalStateObjectDataReplyHandler.class)));

        IEventAggregator eventAggregator = sync.getEventAggregator();
        assertNotNull("Event Aggregator should not be null after connecting", eventAggregator);
        assertEquals("2 Listeners (ObjectStoreListener & PathChangeListener) should be registered", 2, eventAggregator.getListeners().size());
        assertEquals("4 modifiers (RelativePathModifier, AddDirectoryContentModifier, IgnorePathsModifier, SameHashModifier) should be registered", 4, eventAggregator.getModifiers().size());
        assertEquals("1 aggregator (HistoryMoveAggregator) should be registered", 1, eventAggregator.getAggregators().size());

        ScheduledExecutorService backgroundExecutorService = sync.getBackgroundSyncerExecutorService();
        assertNotNull("Background Syncer should be not null after connecting", backgroundExecutorService);
        assertFalse("Background Syncer should not be shut down", backgroundExecutorService.isShutdown());
        assertFalse("Background Syncer should not be terminated", backgroundExecutorService.isTerminated());

        IFileSyncer fileSyncer = sync.getFileSyncer();
        assertNotNull("FileSyncer should not be null after connecting", fileSyncer);

        ISharingSyncer sharingSyncer = sync.getSharingSyncer();
        assertNotNull("Sharing Syncer should not be null after connecting", sharingSyncer);

        IObjectStore objectStore = sync.getObjectStore();
        assertNotNull("Object Store should not be null after connecting", objectStore);

        ITreeStorageAdapter storageAdapter = sync.getStorageAdapter();
        assertEquals("Storage Adapter should still be equal", STORAGE_ADAPTER_1, storageAdapter);

        sync.shutdown();

        // wait until node is stopped
        Thread.sleep(appConfig.getShutdownAnnounceTimeout());

        assertFalse("Node should be stopped", node.isConnected());
        assertTrue("Background Syncer should not be running anymore", backgroundExecutorService.isShutdown());
        assertTrue("Background Syncer should be terminated", backgroundExecutorService.isTerminated());
    }
}
