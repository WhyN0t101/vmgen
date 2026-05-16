package io.vmgen.api.dto.build;

import java.time.Instant;

public record BuildLogDto(
        Long id,
        String logLevel,
        String stage,
        String message,
        Instant loggedAt
) {}
