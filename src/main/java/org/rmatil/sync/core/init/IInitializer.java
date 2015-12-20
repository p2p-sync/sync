package org.rmatil.sync.core.init;

import org.rmatil.sync.core.exception.InitializationException;
import org.rmatil.sync.core.exception.InitializationStartException;
import org.rmatil.sync.core.exception.InitializationStopException;

public interface IInitializer<T> {

    T init() throws InitializationException;

    void start()
            throws InitializationStartException;

    void stop()
            throws InitializationStopException;

}
