package org.rmatil.sync.core.syncer.sharing;

import org.rmatil.sync.core.api.IShareEvent;
import org.rmatil.sync.core.api.ISharingSyncer;
import org.rmatil.sync.core.exception.SharingFailedException;
import org.rmatil.sync.core.messaging.sharingexchange.offer.ShareOfferExchangeHandler;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class SharingSyncer implements ISharingSyncer {

    private static final Logger logger = LoggerFactory.getLogger(SharingSyncer.class);

    protected IClient         client;
    protected  IClientManager  clientManager;
    protected  IStorageAdapter storageAdapter;
    protected  IObjectStore    objectStore;


    public SharingSyncer(IClient client, IClientManager clientManager, IStorageAdapter storageAdapter, IObjectStore objectStore) {
        this.client = client;
        this.clientManager = clientManager;
        this.storageAdapter = storageAdapter;
        this.objectStore = objectStore;
    }

    @Override
    public void sync(IShareEvent sharingEvent) throws SharingFailedException {
        UUID exchangeId = UUID.randomUUID();

        // share event

        // TODO: create UUID (propose) and negotiate sharing with all own clients
        ShareOfferExchangeHandler shareOfferExchangeHandler = new ShareOfferExchangeHandler(
                this.client,
                this.clientManager,
                sharingEvent.getRelativePath().toString(),
                exchangeId
        );

        Thread shareOfferExchangeHandlerThread = new Thread(shareOfferExchangeHandler);
        shareOfferExchangeHandlerThread.setName("ShareOfferExchangeHandler-" + exchangeId);
        shareOfferExchangeHandlerThread.start();

        try {
            shareOfferExchangeHandler.await();
        } catch (InterruptedException e) {
            logger.error("Got interrupted while waiting for sharing exchange " + exchangeId + " to complete. Message: " + e.getMessage() + ". Aborting sharing.");
            throw new SharingFailedException(e);
        }



        // TODO: client has to deny the offering if he already has a fileid for the given path -> send the id back

        // TODO: if any client did not accept -> deny sharing by throwing an exception -> feedback to enduser CLI

        // TODO: send FileShareRequest to sharer and FileSharedRequest to own clients


        // unshare event

        // TODO: negotiate unshare with own clients
        // TODO: send FileUnshareRequest to sharer and FileUnsharedRequest to own clients


    }
}
