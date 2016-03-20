package org.rmatil.sync.core.exception;

public class InitializationStartException extends RuntimeException {

    private static final long serialVersionUID = - 2767930387866882678L;

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
