package org.rmatil.sync.core.init.objectdatareply;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.event.aggregator.core.events.IEvent;
import org.rmatil.sync.network.core.model.ClientDevice;
import org.rmatil.sync.persistence.api.IStorageAdapter;
import org.rmatil.sync.version.api.IObjectStore;

import java.util.List;

/**
 * The initializer for FileOfferRequestHandler
 *
 * @see FileOfferRequestHandler
 */
public class FileOfferRequestReplyInitializer implements IInitializer<FileOfferRequestHandler> {

    /**
     * The client device of this handler
     */
    protected ClientDevice            clientDevice;
    protected IObjectStore            objectStore;
    protected IStorageAdapter         storageAdapter;
    protected FileOfferRequestHandler fileOfferRequestHandler;

    protected final List<IEvent> ignoredEvents;
    protected final List<IEvent> additionalEvents;

    /**
     * @param clientDevice The client device
     */
    public FileOfferRequestReplyInitializer(ClientDevice clientDevice, IObjectStore objectStore, IStorageAdapter storageAdapter, List<IEvent> ignoredEvents, List<IEvent> additionalEvents) {
        this.clientDevice = clientDevice;
        this.objectStore = objectStore;
        this.storageAdapter = storageAdapter;

        this.ignoredEvents = ignoredEvents;
        this.additionalEvents = additionalEvents;
    }

    @Override
    public FileOfferRequestHandler init()
            throws InitializationException {
        this.fileOfferRequestHandler = new FileOfferRequestHandler(this.clientDevice, this.objectStore, this.storageAdapter, this.ignoredEvents, this.additionalEvents);

        return this.fileOfferRequestHandler;
    }

    @Override
    public void start()
            throws InitializationStartException {
        // Nothing to do here
    }

    @Override
    public void stop()
            throws InitializationStopException {
        // Nothing to do here
    }
}
