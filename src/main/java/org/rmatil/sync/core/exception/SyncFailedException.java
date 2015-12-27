package org.rmatil.sync.core.exception;

public class SyncFailedException extends RuntimeException {

    public SyncFailedException() {
        super();
    }

    public SyncFailedException(String message) {
        super(message);
    }

    public SyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyncFailedException(Throwable cause) {
        super(cause);
    }
}
