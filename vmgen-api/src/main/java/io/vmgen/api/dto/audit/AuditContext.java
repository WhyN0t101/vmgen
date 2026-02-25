package io.vmgen.api.dto.audit;

import java.util.Map;

/**
 * Captures HTTP request context for audit logging, including IP address,
 * user agent, and optional structured metadata.
 */
public record AuditContext(
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
) {}


