package io.vmgen.api.dto.worker;

/**
 * Acknowledgement response for worker job operations that only need to
 * confirm receipt or processing of a request.
 */
public record WorkerAckResponse(boolean acknowledged) {
}

