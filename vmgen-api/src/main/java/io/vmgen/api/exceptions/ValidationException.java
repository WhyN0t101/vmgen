package io.vmgen.api.exceptions;

import java.util.List;

public class ValidationException extends VmgenApiException {
    private final List<String> details;

    public ValidationException(String message, List<String> details) {
        super(message);
        this.details = details;
    }

    public List<String> getDetails() {
        return details;
    }
}


