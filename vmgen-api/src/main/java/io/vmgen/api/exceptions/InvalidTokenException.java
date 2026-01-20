package io.vmgen.api.exceptions;

public class InvalidTokenException extends VmgenApiException {
    public InvalidTokenException() {
        super("Invalid authentication token");
    }

    public InvalidTokenException(Throwable cause) {
        super("Invalid authentication token", cause);
    }
}


