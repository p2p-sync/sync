package org.rmatil.sync.core.syncer.background;

import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.syncer.background.fetchobjectstore.FetchObjectStoreExchangeHandler;
import org.rmatil.sync.core.syncer.background.initsync.InitSyncRequest;
import org.rmatil.sync.core.syncer.background.masterelection.MasterElectionExchangeHandler;
import org.rmatil.sync.core.syncer.background.synccomplete.SyncCompleteRequest;
import org.rmatil.sync.core.syncer.background.syncobjectstore.ObjectStoreSyncer;
import org.rmatil.sync.core.syncer.background.syncresult.SyncResultExchangeHandler;

/**
 * Reconciles the entire synchronized folder with all clients.
 * To be unaffected of changes made during this sync, the event aggregator is
 * stopped while syncing and all incoming events are discarded.
 * </p>
 * </p>
 * Reconciliation is done by running through the following steps:
 * <ol>
 * <li>A master peer is selected over all online peers using
 * {@link MasterElectionExchangeHandler}</li>
 * <li>
 * Once a master is found, the master is notified by sending
 * {@link InitSyncRequest}s to all clients.
 * Only the master client will then invoke {@link ObjectStoreSyncer}
 * and traverse the following steps:
 * <ol>
 * <li>Fetch all object stores from all other clients
 * ({@link FetchObjectStoreExchangeHandler})</li>
 * <li>Compare the object stores and remove obsolete files
 * resp. fetch missing files from the corresponding clients
 * ({@link FileDemandExchangeHandler})</li>
 * <li>After having established a merged object store and
 * synchronized folder, send the merged object store to all
 * other clients ({@link SyncResultExchangeHandler})</li>
 * <li>All notified clients will then compare their object
 * store with the received one and also remove deleted paths
 * resp. fetch missing ones ({@link FileDemandExchangeHandler})</li>
 * <li>After having completed the synchronization on all
 * clients, the {@link ObjectStoreSyncer} will send a {@link SyncCompleteRequest} to all clients, which restart their event aggregator and publish changes made in the mean time</li>
 * </ol>
 * </li>
 * </ol>
 */
public interface IBackgroundSyncer extends Runnable {

}
