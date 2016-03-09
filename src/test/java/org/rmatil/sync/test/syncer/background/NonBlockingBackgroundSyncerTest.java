package org.rmatil.sync.test.syncer.background;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.commons.hashing.HashingAlgorithm;
import org.rmatil.sync.commons.path.Naming;
import org.rmatil.sync.core.eventbus.CreateBusEvent;
import org.rmatil.sync.core.eventbus.IgnoreBusEvent;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequestHandler;
import org.rmatil.sync.core.syncer.background.NonBlockingBackgroundSyncer;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequest;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreRequestHandler;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.event.aggregator.core.events.MoveEvent;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.tree.TreePathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.rmatil.sync.test.messaging.base.BaseNetworkHandlerTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class NonBlockingBackgroundSyncerTest extends BaseNetworkHandlerTest {

    private static final Logger logger = LoggerFactory.getLogger(NonBlockingBackgroundSyncer.class);

    protected static final Path CONFLICTING_FILE = Paths.get("conflictFile.txt");
    protected static final Path DELETED_FILE     = Paths.get("deletedFile.txt");
    protected static final Path MISSING_FILE     = Paths.get("missingFile.txt");
    protected static final Path OUTDATED_PATH    = Paths.get("outdatedFile.txt");

    protected static final Handler EVENT_HANDLER = new Handler();

    /**
     * A handler for global bus events
     */
    static class Handler {

        public Queue<IEvent> createEvents = new ConcurrentLinkedQueue<>();
        public Queue<IEvent> ignoreEvents = new ConcurrentLinkedQueue<>();

        @net.engio.mbassy.listener.Handler
        public void handleBusEvent(CreateBusEvent createBusEvent) {
            logger.info("Got createBus event " + createBusEvent.getEvent().getEventName());
            this.createEvents.add(createBusEvent.getEvent());
        }

        @net.engio.mbassy.listener.Handler
        public void handleBusEvent2(IgnoreBusEvent ignoreBusEvent) {
            logger.info("Got ignoreBus event " + ignoreBusEvent.getEvent().getEventName());
            this.ignoreEvents.add(ignoreBusEvent.getEvent());
        }
    }

    @BeforeClass
    public static void setUpChild()
            throws IOException, InputOutputException, InterruptedException {
        GLOBAL_EVENT_BUS_1.subscribe(EVENT_HANDLER);

        TreePathElement conflictFile = new TreePathElement(CONFLICTING_FILE.toString());

        // create a file which is conflicting on both files
        STORAGE_ADAPTER_1.persist(StorageType.FILE, conflictFile, "Some content for the first file".getBytes());
        STORAGE_ADAPTER_2.persist(StorageType.FILE, conflictFile, "Some other content for the second file to cause a conflict".getBytes());

        // create the file which is deleted on client2 but not on client1 yet
        TreePathElement deletedFile = new TreePathElement(DELETED_FILE.toString());
        STORAGE_ADAPTER_1.persist(StorageType.FILE, deletedFile, "Some deleted file".getBytes());
        STORAGE_ADAPTER_2.persist(StorageType.FILE, deletedFile, "Some deleted file".getBytes());

        // create the file which is missing on client1
        TreePathElement missingFile = new TreePathElement(MISSING_FILE.toString());
        STORAGE_ADAPTER_2.persist(StorageType.FILE, missingFile, "Some missing file".getBytes());

        // create the file which is outdated on client1
        TreePathElement outdatedFile = new TreePathElement(OUTDATED_PATH.toString());
        STORAGE_ADAPTER_1.persist(StorageType.FILE, outdatedFile, "Some outdated content".getBytes());
        STORAGE_ADAPTER_2.persist(StorageType.FILE, outdatedFile, "Some outdated content".getBytes());

        OBJECT_STORE_1.sync(ROOT_TEST_DIR1.toFile());
        OBJECT_STORE_2.sync(ROOT_TEST_DIR2.toFile());

        // remove file on storage adapter 2
        OBJECT_STORE_2.onRemoveFile(DELETED_FILE.toString());
        STORAGE_ADAPTER_2.delete(deletedFile);

        // update outdated path on client2 so that client1 will have to re-download it
        STORAGE_ADAPTER_2.persist(StorageType.FILE, outdatedFile, "Some updated content".getBytes());
        OBJECT_STORE_2.onModifyFile(outdatedFile.getPath(), Hash.hash(HashingAlgorithm.SHA_256, "Some updated content".getBytes()));

        // register request handlers
        CLIENT_2.getObjectDataReplyHandler().addRequestCallbackHandler(FileDemandRequest.class, FileDemandRequestHandler.class);
        CLIENT_2.getObjectDataReplyHandler().addRequestCallbackHandler(FetchObjectStoreRequest.class, FetchObjectStoreRequestHandler.class);

        EVENT_AGGREGATOR_1.start();
        EVENT_AGGREGATOR_2.start();
    }

    @Test
    public void test()
            throws InputOutputException, InterruptedException, IOException {
        NonBlockingBackgroundSyncer nonBlockingBackgroundSyncer = new NonBlockingBackgroundSyncer(
                EVENT_AGGREGATOR_1,
                CLIENT_1,
                CLIENT_MANAGER_1,
                OBJECT_STORE_1,
                STORAGE_ADAPTER_1,
                GLOBAL_EVENT_BUS_1,
                new ArrayList<>(),
                new ArrayList<>()
        );

        // we "start" the thread manually to not wait for its completion

        Thread thread = new Thread(nonBlockingBackgroundSyncer);
        thread.setName("NonBlockingBackgroundSyncer-TEST");
        thread.start();

        while (thread.getState() != Thread.State.TERMINATED) {
            Thread.sleep(1000L);
        }

        // we expect the following files to exist
        // - conflictFile.txt
        // - conflictFile_<ClientId1>
        assertTrue("Original file should still exist", STORAGE_ADAPTER_1.exists(StorageType.FILE, new TreePathElement(CONFLICTING_FILE.toString())));
        TreePathElement conflictFile = new TreePathElement(Naming.getConflictFileName(CONFLICTING_FILE.toString(), true, "txt", CLIENT_1.getClientDeviceId().toString()));
        assertTrue("Conflict file should exist", STORAGE_ADAPTER_1.exists(StorageType.FILE, conflictFile));

        // check that events are contained from conflict handler

        // modify event is originating from backgroundSyncer's check after restarting the event aggregator:
        // there, he will detect that the conflict file has been created. There is no modify event for the original
        // conflict file since its content hash is merged from the other client
        assertEquals("IgnoreEvents must contain move and modify event for the conflict file, the create event for the misingFile, the create event for the orig. conflict file and a modify event for the outdated one", 5, EVENT_HANDLER.ignoreEvents.size());

        // this is the move event for the conflicting file on our side
        IEvent moveEvent = EVENT_HANDLER.ignoreEvents.poll();
        Assert.assertThat("Event must be instance of move event", moveEvent, instanceOf(MoveEvent.class));
        assertEquals("IgnoreEvents' move event must contain the correct old path for the conflict file", moveEvent.getPath().toString(), CONFLICTING_FILE.toString());
        assertEquals("IgnoreEvents' move event must contain the correct new path for the conflict file", ((MoveEvent) moveEvent).getNewPath().toString(), conflictFile.getPath());

        // this is the ignore event for the modify event after the final reconciliation for the conflict file
        IEvent modifyEvent = EVENT_HANDLER.ignoreEvents.poll();
        Assert.assertThat("Event must be instance of modify event", modifyEvent, instanceOf(ModifyEvent.class));
        assertEquals("IgnoreEvents' modify event must contain the correct path for the conflict file", conflictFile.getPath(), modifyEvent.getPath().toString());

        // this is the ignore event for the create event of the missing file
        IEvent createEvent = EVENT_HANDLER.ignoreEvents.poll();
        Assert.assertThat("Event must be instance of create event", createEvent, instanceOf(CreateEvent.class));
        assertEquals("IgnoreEvents' create event must contain the correct path for the conflict file", MISSING_FILE.toString(), createEvent.getPath().toString());

        IEvent createEvent2 = EVENT_HANDLER.ignoreEvents.poll();
        Assert.assertThat("Event must be instance of create event", createEvent2, instanceOf(CreateEvent.class));
        assertEquals("IgnoreEvents' create event must contain the correct path for the conflict file", CONFLICTING_FILE.toString(), createEvent2.getPath().toString());

        IEvent modifyEvent3 = EVENT_HANDLER.ignoreEvents.poll();
        Assert.assertThat("Event must be instance of modify event", modifyEvent3, instanceOf(ModifyEvent.class));
        assertEquals("IgnoreEvents' modify event must contain the correct path for the outdated file", OUTDATED_PATH.toString(), modifyEvent3.getPath().toString());


        // this is the create event for the conflict file (ConflictHandler)
        assertEquals("CreateEvents must contain a create and modify event for the conflict file", 2, EVENT_HANDLER.createEvents.size());
        IEvent createEvent3 = EVENT_HANDLER.createEvents.poll();
        Assert.assertThat("Event must be instance of create event", createEvent3, instanceOf(CreateEvent.class));
        assertEquals("CreateEvent must be for the conflict file", conflictFile.getPath(), createEvent3.getPath().toString());

        // this modify event is resulting from the final reconciliation between the merged object store and the actual disk contents
        IEvent modifyEvent2 = EVENT_HANDLER.createEvents.poll();
        Assert.assertThat("Event must be instance of modify event", modifyEvent2, instanceOf(ModifyEvent.class));
        assertEquals("Modify must be for the conflict file", conflictFile.getPath(), modifyEvent2.getPath().toString());

        // check deletion
        TreePathElement deletedFile = new TreePathElement(DELETED_FILE.toString());
        assertFalse("Deleted file should not exist anymore on client1", STORAGE_ADAPTER_1.exists(StorageType.FILE, deletedFile));

        // check that missing file was fetched
        TreePathElement missingFile = new TreePathElement(MISSING_FILE.toString());
        assertTrue("Missing file should be fetched on client1", STORAGE_ADAPTER_1.exists(StorageType.FILE, missingFile));

        // check that the outdated file was fetched
        String updatedContent = new String(Files.readAllBytes(Paths.get(STORAGE_ADAPTER_1.getRootDir().getPath()).resolve(OUTDATED_PATH)));

        assertEquals("Content should be the same", "Some updated content", updatedContent);
    }

}
