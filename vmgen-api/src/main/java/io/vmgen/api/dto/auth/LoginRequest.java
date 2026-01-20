package io.vmgen.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(min = 1, max = 255) String password
) {}


