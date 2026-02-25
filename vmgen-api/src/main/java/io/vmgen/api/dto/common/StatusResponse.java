package io.vmgen.api.dto.common;

/**
 * Simple status response wrapper used for endpoints that only need to
 * signal a textual outcome such as {@code "ok"}.
 */
public record StatusResponse(String status) {
}

