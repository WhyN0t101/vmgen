package io.vmgen.api.dto.auth;

import java.time.Instant;

public record JwtClaimsDto(
        String userId,
        String role,
        String sessionId,
        Instant issuedAt,
        Instant expiresAt
) {}


