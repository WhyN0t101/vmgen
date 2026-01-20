package io.vmgen.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vmgen.jwt")
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

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getUserTtlSeconds() {
        return userTtlSeconds;
    }

    public void setUserTtlSeconds(long userTtlSeconds) {
        this.userTtlSeconds = userTtlSeconds;
    }

    public long getAdminTtlSeconds() {
        return adminTtlSeconds;
    }

    public void setAdminTtlSeconds(long adminTtlSeconds) {
        this.adminTtlSeconds = adminTtlSeconds;
    }
}


