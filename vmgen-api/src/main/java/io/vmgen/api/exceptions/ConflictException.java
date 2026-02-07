package io.vmgen.api.exceptions;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }

    public String getPublicMessage() {
        return "Invalid or expired token";
    }
}
