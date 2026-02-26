package io.vmgen.api.exceptions;

/**
 * Generic 409-Conflict style exception for domain conflicts (e.g. duplicate
 * identifiers, invalid state transitions). Uses the provided message as the
 * user-facing public message.
 */
public class ConflictException extends VmgenApiException {
    public ConflictException(String message) {
        super(message);
    }
}

