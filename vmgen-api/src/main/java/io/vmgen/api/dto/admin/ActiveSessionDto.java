package io.vmgen.api.dto.admin;

/**
 * DTO representing an active session with associated user information,
 * used by admin APIs to inspect and manage current sessions.
 */
public record ActiveSessionDto(
        String sessionId,
        String userId,
        String username,
        String role,
        String createdAt,
        String lastActivityAt,
        String expiresAt
) {}


