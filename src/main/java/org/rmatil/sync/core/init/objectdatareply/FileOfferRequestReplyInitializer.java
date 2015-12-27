package org.rmatil.sync.core.init.objectdatareply;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferRequestHandler;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponseHandler;
import org.rmatil.sync.core.model.ClientDevice;

/**
 * The initializer for FileOfferResponseHandlers
 *
 * @see FileOfferResponseHandler
 */
public class FileOfferRequestReplyInitializer implements IInitializer<FileOfferRequestHandler> {

    /**
     * The client device of this handler
     */
    protected ClientDevice            clientDevice;
    protected FileOfferRequestHandler fileOfferRequestHandler;

    /**
     * @param clientDevice The client device
     */
    public FileOfferRequestReplyInitializer(ClientDevice clientDevice) {
        this.clientDevice = clientDevice;
    }

    @Override
    public FileOfferRequestHandler init()
            throws InitializationException {
        this.fileOfferRequestHandler = new FileOfferRequestHandler(this.clientDevice);

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
