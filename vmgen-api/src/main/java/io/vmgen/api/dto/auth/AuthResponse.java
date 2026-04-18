package io.vmgen.api.dto.auth;

import java.util.UUID;

/**
 * Response returned after a successful authentication, containing the
 * issued JWT token, its expiry timestamp, the user's role, and userId.
 */
public record AuthResponse(
        String token,
        String expiresAt,
        String role,
        UUID userId
) {}


