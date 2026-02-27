package io.vmgen.api.exceptions;

/**
 * Thrown when adding or resolving script dependencies would introduce a cycle.
 */
public class CircularDependencyException extends VmgenApiException {

    public CircularDependencyException(String message) {
        super(message);
    }
}

