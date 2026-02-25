package io.vmgen.api.dto.auth;

/**
 * Response returned after a successful authentication, containing the
 * issued JWT token, its expiry timestamp, and the user's role.
 */
public record AuthResponse(
        String token,
        String expiresAt,
        String role
) {}


