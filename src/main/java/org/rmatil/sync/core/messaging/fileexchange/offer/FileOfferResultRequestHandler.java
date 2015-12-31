package org.rmatil.sync.core.messaging.fileexchange.offer;

import net.tomp2p.peers.PeerAddress;
import net.tomp2p.rpc.ObjectDataReply;
import org.rmatil.sync.commons.hashing.Hash;
import org.rmatil.sync.commons.hashing.HashingAlgorithm;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandler;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandExchangeHandlerResult;
import org.rmatil.sync.core.messaging.fileexchange.demand.FileDemandRequest;
import org.rmatil.sync.event.aggregator.core.events.CreateEvent;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.event.aggregator.core.events.ModifyEvent;
import org.rmatil.sync.network.api.IClient;
import org.rmatil.sync.network.api.IClientManager;
import org.rmatil.sync.network.api.IUser;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IPathElement;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.persistence.api.StorageType;
import org.rmatil.sync.persistence.core.local.LocalPathElement;
import org.rmatil.sync.persistence.exceptions.InputOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class FileOfferResultRequestHandler implements ObjectDataReply {

    private static final Logger logger = LoggerFactory.getLogger(FileOfferResultRequestHandler.class);

    /**
     * The storage adapter for the synchronized folder
     */
    protected IStorageAdapter storageAdapter;

    protected IUser user;

    protected IClient client;

    protected IClientManager clientManager;

    protected ClientDevice clientDevice;

    protected int chunkCounter;

    protected ExecutorCompletionService<FileDemandExchangeHandlerResult> completionService;

    protected final List<IEvent> eventsToIgnore;
    protected final List<IEvent> eventsToAdditionallyAdd;

    /**
     * @param storageAdapter The storage adapter for the synchronized folder
     */
    public FileOfferResultRequestHandler(IStorageAdapter storageAdapter, IUser user, IClient client, IClientManager clientManager, ClientDevice clientDevice, List<IEvent> ignoredEvents, List<IEvent> additionalEvents) {
        this.storageAdapter = storageAdapter;
        this.user = user;
        this.client = client;
        this.clientManager = clientManager;
        this.clientDevice = clientDevice;
        this.chunkCounter = 0;

        this.eventsToIgnore = ignoredEvents;
        this.eventsToAdditionallyAdd = additionalEvents;

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        this.completionService = new ExecutorCompletionService<>(executorService);
    }

    @Override
    public Object reply(PeerAddress sender, Object request)
            throws Exception {

        if (! (request instanceof FileOfferResultRequest)) {
            logger.error("Received an unknown file request. Aborting...");
            return null;
        }

        FileOfferResultRequest fileOfferResultRequest = (FileOfferResultRequest) request;

        if (fileOfferResultRequest.hasConflict()) {
            logger.debug("FileOfferResultRequest has a conflict");
            // then we request each conflict file for of the corresponding client
            for (Map.Entry<String, ClientDevice> entry : fileOfferResultRequest.getConflictFiles().entrySet()) {
                logger.info("Requesting conflict file " + entry.getKey() + " from client " + entry.getValue().getPeerAddress().inetAddress().getHostAddress() + ":" + entry.getValue().getPeerAddress().tcpPort());
                this.fetchFile(entry.getKey(), entry.getValue());
            }
        } else {
            // if not, we check, if we have that file, and request it if we do not
            for (Map.Entry<String, ClientDevice> entry : fileOfferResultRequest.getConflictFiles().entrySet()) {
                IPathElement pathElement = new LocalPathElement(entry.getKey());

                if (! this.storageAdapter.exists(StorageType.FILE, pathElement)) {
                    logger.info("Requesting not existing file " + entry.getKey() + " from client " + entry.getValue().getPeerAddress().inetAddress().getHostAddress() + ":" + entry.getValue().getPeerAddress().tcpPort());
                    this.fetchFile(entry.getKey(), entry.getValue());
                }
            }
        }

        return null;
    }


    protected void fetchFile(String filePath, ClientDevice clientToFetchFrom) {
        FileDemandRequest fileDemandRequest = new FileDemandRequest(
                UUID.randomUUID(),
                this.clientDevice,
                filePath,
                this.chunkCounter
        );

        this.fetchChunk(fileDemandRequest, clientToFetchFrom);

        // TODO: check that all paths to the file exist (i.e. directories)

        // we wait here for the first request to complete
        Future<FileDemandExchangeHandlerResult> initialChunkRequestResult;
        try {
            initialChunkRequestResult = this.completionService.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("Got interrupted while waiting for the initial chunk request to complete. Message: " + e.getMessage(), e);
        }

        FileDemandExchangeHandlerResult result;
        try {
            result = initialChunkRequestResult.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Getting the result of the future failed. Message: " + e.getMessage(), e);
        }

        this.chunkCounter++;

        IPathElement pathElement = new LocalPathElement(filePath);

        // create placeholder file of total size with first chunk in it
        long offset = 0;
        long dataToWrite = result.getTotalFileSize();
        while (offset < dataToWrite) {
            int maxData = Integer.MAX_VALUE;

            // this should be a safe conversion since the rest is smaller than int max_value
            if ((dataToWrite - offset) < maxData) {
                maxData = (int) (dataToWrite - offset);
            }

            byte[] content = new byte[maxData];

            IEvent eventToIgnore;
            if (0 == offset) {
                eventToIgnore = new CreateEvent(
                        Paths.get(filePath),
                        Paths.get(filePath).getFileName().toString(),
                        Hash.hash(HashingAlgorithm.SHA_256, content),
                        System.currentTimeMillis()
                );
            } else {
                eventToIgnore = new ModifyEvent(
                        Paths.get(filePath),
                        Paths.get(filePath).getFileName().toString(),
                        Hash.hash(HashingAlgorithm.SHA_256, content),
                        System.currentTimeMillis()
                );
            }

            synchronized (this.eventsToIgnore) {
                this.eventsToIgnore.add(eventToIgnore);
            }

            try {
                this.storageAdapter.persist(StorageType.FILE, pathElement, offset, content);
            } catch (InputOutputException e) {
                throw new RuntimeException("Failed to write initial empty file. Message: " + e.getMessage(), e);
            }

            offset += maxData;
        }

        // now write the first chunk
        try {
            synchronized (this.eventsToIgnore) {
                this.eventsToIgnore.add(new ModifyEvent(
                        Paths.get(pathElement.getPath()),
                        Paths.get(pathElement.getPath()).getFileName().toString(),
                        Hash.hash(HashingAlgorithm.SHA_256, result.getData().getContent()),
                        System.currentTimeMillis()
                ));
            }

            this.storageAdapter.persist(StorageType.FILE, pathElement, 0, result.getData().getContent());
        } catch (InputOutputException e) {
            throw new RuntimeException("Failed to write initial chunk. Message: " + e.getMessage(), e);
        }

        // fetch all single chunks
        while (result.getChunkCounter() < result.getTotalNrOfChunks()) {
            this.chunkCounter++;

            this.fetchChunk(new FileDemandRequest(
                    UUID.randomUUID(),
                    this.clientDevice,
                    filePath,
                    this.chunkCounter
            ), clientToFetchFrom);
        }


        // now we wait for each future to complete
        // fetch all results until none is available anymore
        Future<FileDemandExchangeHandlerResult> futureResult = this.completionService.poll();

        // start at one, since we got the initial chunk request already
        int receivedChunks = 1;
        while (receivedChunks < this.chunkCounter) {
            if (null != futureResult) {
                try {
                    FileDemandExchangeHandlerResult chunk = futureResult.get();
                    try {
                        byte[] existingBytes = this.storageAdapter.read(pathElement);
                        // TODO: fix writing with "long" offset
                        int intOffset = (int) offset;

                        int newTotalSize;
                        if (intOffset < existingBytes.length) {
                            newTotalSize = existingBytes.length - Math.abs(intOffset - existingBytes.length) + chunk.getData().getContent().length;
                        } else {
                            newTotalSize = Math.min(intOffset, existingBytes.length) + chunk.getData().getContent().length;
                            intOffset = newTotalSize - chunk.getData().getContent().length;
                        }

                        if (newTotalSize < existingBytes.length) {
                            newTotalSize = existingBytes.length;
                        }

                        byte[] targetBytes = new byte[newTotalSize];

                        int maxAllowedWriteSize;
                        if (existingBytes.length > newTotalSize) {
                            maxAllowedWriteSize = newTotalSize;
                        } else {
                            maxAllowedWriteSize = existingBytes.length;
                        }

                        System.arraycopy(existingBytes, 0, targetBytes, 0, maxAllowedWriteSize);
                        System.arraycopy(chunk.getData().getContent(), 0, targetBytes, intOffset, chunk.getData().getContent().length);

                        synchronized (this.eventsToIgnore) {
                            this.eventsToIgnore.add(new ModifyEvent(
                                    Paths.get(filePath),
                                    Paths.get(filePath).getFileName().toString(),
                                    Hash.hash(HashingAlgorithm.SHA_256, targetBytes),
                                    System.currentTimeMillis()
                            ));
                        }

                        this.storageAdapter.persist(StorageType.FILE, pathElement, chunk.getChunkCounter() * chunk.getChunkSize(), chunk.getData().getContent());
                    } catch (InputOutputException e) {
                        logger.error("Can not write chunk for file " + pathElement.getPath() + " to disk. Message: " + e.getMessage());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to get chunk from future. Message: " + e.getMessage(), e);
                }

                receivedChunks++;
            } else {
                // no response ready yet, we wait
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    logger.error("Got interrupted while waiting for response of any fileDemandRequest");
                }
            }

            futureResult = this.completionService.poll();
        }
    }


    protected void fetchChunk(FileDemandRequest fileDemandRequest, ClientDevice clientToFetchFrom) {
        FileDemandExchangeHandler fileDemandExchangeHandler = new FileDemandExchangeHandler(
                this.storageAdapter,
                this.user,
                this.clientManager,
                this.client,
                clientToFetchFrom,
                fileDemandRequest
        );

        logger.debug("Starting to FileDemandHandler for fileExchangeId " + fileDemandRequest.getExchangeId());
        this.completionService.submit(fileDemandExchangeHandler);
    }
}
