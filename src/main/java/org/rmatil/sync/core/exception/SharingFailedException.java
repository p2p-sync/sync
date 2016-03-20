package org.rmatil.sync.core.exception;

public class SharingFailedException extends RuntimeException {

    private static final long serialVersionUID = - 7042431609585659719L;

    public SharingFailedException() {
        super();
    }

    public SharingFailedException(String message) {
        super(message);
    }

    public SharingFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharingFailedException(Throwable cause) {
        super(cause);
    }
}
