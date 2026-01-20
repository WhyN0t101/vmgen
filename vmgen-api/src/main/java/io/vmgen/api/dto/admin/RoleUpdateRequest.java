package io.vmgen.api.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RoleUpdateRequest(
        @NotBlank
        @Pattern(regexp = "^(user|admin)$", message = "role must be 'user' or 'admin'")
        String role
) {}


