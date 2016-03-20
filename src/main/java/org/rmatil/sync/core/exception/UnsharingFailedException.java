package org.rmatil.sync.core.exception;

public class UnsharingFailedException extends RuntimeException {

    private static final long serialVersionUID = 256384993647642082L;

    public UnsharingFailedException() {
        super();
    }

    public UnsharingFailedException(String message) {
        super(message);
    }

    public UnsharingFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnsharingFailedException(Throwable cause) {
        super(cause);
    }
}
