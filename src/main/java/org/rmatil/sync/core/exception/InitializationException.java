package org.rmatil.sync.core.exception;

public class InitializationException extends RuntimeException {

    private static final long serialVersionUID = 7753809234117076822L;

    public InitializationException() {
        super();
    }

    public InitializationException(String message) {
        super(message);
    }

    public InitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public InitializationException(Throwable cause) {
        super(cause);
    }

}
