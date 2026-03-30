package io.vmgen.api.dto.telemetry;

import java.time.Instant;
import java.util.UUID;

public record TelemetryUploadResponse(
        UUID id,
        UUID buildId,
        String fileSha256,
        long fileSizeBytes,
        Instant uploadedAt,
        String rawJson
) {}
