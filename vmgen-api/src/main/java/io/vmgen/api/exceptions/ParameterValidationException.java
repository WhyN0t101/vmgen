package io.vmgen.api.exceptions;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when one or more script parameter values fail validation.
 */
public class ParameterValidationException extends VmgenApiException {

    private final List<String> errors;

    public ParameterValidationException(String message, List<String> errors) {
        super(message);
        this.errors = List.copyOf(errors);
    }

    public ParameterValidationException(String message) {
        this(message, Collections.singletonList(message));
    }

    public List<String> getErrors() {
        return errors;
    }
}

