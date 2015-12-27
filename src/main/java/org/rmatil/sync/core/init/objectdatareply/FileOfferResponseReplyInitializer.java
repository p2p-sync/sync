package org.rmatil.sync.core.init.objectdatareply;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;
import org.rmatil.sync.core.init.IInitializer;
import org.rmatil.sync.core.messaging.fileexchange.offer.FileOfferResponseHandler;

/**
 * The initializer for FileOfferResponseHandlers
 *
 * @see FileOfferResponseHandler
 */
public class FileOfferResponseReplyInitializer implements IInitializer<FileOfferResponseHandler> {

    protected FileOfferResponseHandler fileOfferResponseHandler;

    @Override
    public FileOfferResponseHandler init()
            throws InitializationException {
        this.fileOfferResponseHandler = new FileOfferResponseHandler();

        return this.fileOfferResponseHandler;
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
