package io.vmgen.api.dto.admin;

public record ActiveSessionDto(
        String sessionId,
        String userId,
        String username,
        String role,
        String createdAt,
        String lastActivityAt,
        String expiresAt
) {}


