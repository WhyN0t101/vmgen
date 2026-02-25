package io.vmgen.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for creating a new user account.
 * Enforces basic constraints on username, email, and password length.
 */
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 255) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 255) String password
) {}


