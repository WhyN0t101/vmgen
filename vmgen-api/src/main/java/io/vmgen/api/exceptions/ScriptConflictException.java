package io.vmgen.api.exceptions;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a set of scripts contains mutually exclusive combinations.
 */
public class ScriptConflictException extends VmgenApiException {

    private final List<String> conflicts;

    public ScriptConflictException(String message, List<String> conflicts) {
        super(message);
        this.conflicts = List.copyOf(conflicts);
    }

    public ScriptConflictException(String message) {
        this(message, Collections.emptyList());
    }

    public List<String> getConflicts() {
        return conflicts;
    }
}

