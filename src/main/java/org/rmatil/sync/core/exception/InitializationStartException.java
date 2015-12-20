package org.rmatil.sync.core.exception;

public class InitializationStartException extends RuntimeException {

    public InitializationStartException() {
        super();
    }

    public InitializationStartException(String message) {
        super(message);
    }

    public InitializationStartException(String message, Throwable cause) {
        super(message, cause);
    }

    public InitializationStartException(Throwable cause) {
        super(cause);
    }

}
