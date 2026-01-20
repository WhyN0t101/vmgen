package io.vmgen.api.dto.audit;

import java.util.Map;

public record AuditContext(
        String ipAddress,
        String userAgent,
        Map<String, Object> metadata
) {}


