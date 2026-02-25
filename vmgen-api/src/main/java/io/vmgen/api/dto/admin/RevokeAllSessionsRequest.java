package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request to revoke all active sessions for a given user.
 * Carries the user ID as a string and exposes a parsed {@link UUID}.
 */
public record RevokeAllSessionsRequest(@NotBlank String userId) {
    public UUID userUuid() {
        return UUID.fromString(userId);
    }
}

