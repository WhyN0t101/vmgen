package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request to revoke a single session by its string identifier.
 * Provides a helper to convert the ID to a {@link UUID}.
 */
public record RevokeSessionRequest(
        @NotBlank String sessionId
) {
    public UUID sessionUuid() {
        return UUID.fromString(sessionId);
    }
}