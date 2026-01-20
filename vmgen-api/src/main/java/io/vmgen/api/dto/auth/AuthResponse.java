package io.vmgen.api.dto.auth;

public record AuthResponse(
        String token,
        String expiresAt,
        String role
) {}


