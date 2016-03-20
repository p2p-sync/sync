package org.rmatil.sync.core.exception;

public class InitializationStopException extends RuntimeException {

    private static final long serialVersionUID = 6459729184360894692L;

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
