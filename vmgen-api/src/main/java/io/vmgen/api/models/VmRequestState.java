package io.vmgen.api.models;

/**
 * VM Request state machine states.
 */
public enum VmRequestState {
    QUEUED,
    BUILDING,
    EXPORTING,
    READY,
    FAILED,
    ARCHIVED
}
