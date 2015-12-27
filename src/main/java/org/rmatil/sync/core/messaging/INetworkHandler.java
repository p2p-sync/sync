package org.rmatil.sync.core.messaging;

import org.rmatil.sync.core.exception.SyncFailedException;

import java.util.concurrent.Callable;

/**
 * Handles the protocol implementation between clients to exchange
 * information like files, meta information and more.
 * <p>
 * Must implement Callable to be able to be run asynchronously.
 *
 * @param <T> The result type which should be returned, once the information exchange is completed and the protocol steps are performed
 */
public interface INetworkHandler<T> extends Callable<T> {

    /**
     * The main method which starts the protocol implementation.
     * <p>
     * {@inheritDoc}
     *
     * @return The result after all protocol steps are performed. May be null
     */
    T call();

    /**
     * A response from a client to add the network handler so that
     * he can continue the protocol definition.
     *
     * @param response A response from a client corresponding to the particular requests made by the implementing network handler
     *
     * @throws SyncFailedException If the response is not of the type handled by the network handler implementation
     */
    void addResponse(IResponse response)
            throws SyncFailedException;


    /**
     * Returns true, once all necessary clients have responded
     *
     * @return True, if all necessary clients habe been responded. False otherwise.
     */
    boolean allClientResponded();

}
