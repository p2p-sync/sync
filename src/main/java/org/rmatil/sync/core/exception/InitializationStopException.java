package org.rmatil.sync.core.exception;

public class InitializationStopException extends RuntimeException {

    public InitializationStopException() {
        super();
    }

    public InitializationStopException(String message) {
        super(message);
    }

    public InitializationStopException(String message, Throwable cause) {
        super(message, cause);
    }

    public InitializationStopException(Throwable cause) {
        super(cause);
    }

}
