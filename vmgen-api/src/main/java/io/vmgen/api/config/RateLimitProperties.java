package io.vmgen.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vmgen.rate-limit.login")
/*
 * Configuration for login rate limiting (per-IP request limits and window).
 * Used by {@code RateLimitService} to protect authentication endpoints.
 */
public class RateLimitProperties {

    /**
     * Maximum number of login requests allowed per window.
     */
    private int maxRequests = 5;

    /**
     * Time window in seconds.
     */
    private long windowSeconds = 300; // 5 minutes
}
