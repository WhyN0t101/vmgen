package io.vmgen.api.exceptions;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(Throwable cause) {
        super("Invalid token", cause);
    }

    public String getPublicMessage() {
        return "Invalid or expired token";
    }
}