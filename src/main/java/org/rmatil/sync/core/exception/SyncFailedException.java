package org.rmatil.sync.core.exception;

public class SyncFailedException extends RuntimeException {

    private static final long serialVersionUID = 1269047290321287484L;

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
