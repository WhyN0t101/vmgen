package io.vmgen.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "vmgen.jwt")
/**
 * Strongly-typed configuration for JWT issuance and validation,
 * including HS256 secret material, issuer, and token lifetimes.
 */
public class JwtProperties {
    /**
     * HS256 secret. Must be at least 32 bytes (256 bits).
     */
    private String secret;

    private String issuer = "vmgen-api";

    private long userTtlSeconds = 3600;

    private long adminTtlSeconds = 1800;

    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("vmgen.jwt.secret must be configured");
        }
        // Minimum 32 bytes for HS256
        if (secret.getBytes().length < 32) {
            throw new IllegalStateException("vmgen.jwt.secret must be at least 32 bytes for HS256");
        }
    }

}


