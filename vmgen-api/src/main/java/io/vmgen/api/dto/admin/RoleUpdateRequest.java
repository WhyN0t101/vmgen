package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Admin request to change a user's role.
 * Accepts {@code USER} or {@code ADMIN} as uppercase enum names.
 */
public record RoleUpdateRequest(
        @NotBlank
        @Pattern(regexp = "^(USER|ADMIN)$", message = "role must be 'user' or 'admin'")
        String role
) {}


