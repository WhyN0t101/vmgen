package io.vmgen.api.dto.worker;

/**
 * Response indicating that a worker job has been successfully registered
 * as completed by the backend.
 */
public record WorkerRegisteredResponse(boolean registered) {
}

