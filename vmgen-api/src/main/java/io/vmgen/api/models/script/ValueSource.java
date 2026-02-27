package io.vmgen.api.models.script;

/**
 * Describes how a parameter value is obtained at request time.
 */
public enum ValueSource {
    USER_INPUT,
    AUTO_RANDOM,
    AUTO_CHOICE,
    USER_INPUT_OR_RANDOM
}

