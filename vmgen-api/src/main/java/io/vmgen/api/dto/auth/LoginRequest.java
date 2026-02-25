package io.vmgen.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login credentials for a user, allowing either username or email as
 * the {@code identifier} plus a plaintext password for verification.
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(min = 1, max = 255) String password
) {}


