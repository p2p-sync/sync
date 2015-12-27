package org.rmatil.sync.core.exception;

public class ObjectSendFailedException extends RuntimeException {

    public ObjectSendFailedException() {
        super();
    }

    public ObjectSendFailedException(String message) {
        super(message);
    }

    public ObjectSendFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectSendFailedException(Throwable cause) {
        super(cause);
    }
}
