package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RevokeAllSessionsRequest(@NotBlank String userId) {
    public UUID userUuid() {
        return UUID.fromString(userId);
    }
}


