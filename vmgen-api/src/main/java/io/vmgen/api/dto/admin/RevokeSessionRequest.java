package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RevokeSessionRequest(@NotBlank String sessionId) {
    public UUID sessionUuid() {
        return UUID.fromString(sessionId);
    }
}


