package io.vmgen.api.dto.auth;

import java.time.Instant;

/**
 * Parsed and validated claims extracted from a JWT, used internally by
 * the authentication filter and services.
 */
public record JwtClaimsDto(
        String userId,
        String role,
        String sessionId,
        Instant issuedAt,
        Instant expiresAt
) {}


