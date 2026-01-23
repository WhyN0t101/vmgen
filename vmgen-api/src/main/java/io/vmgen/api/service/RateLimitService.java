package io.vmgen.api.service;

import io.vmgen.api.config.RateLimitProperties;
import io.vmgen.api.exceptions.RateLimitExceededException;
import io.vmgen.api.utils.RateLimiter;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private final RateLimiter loginRateLimiter;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.loginRateLimiter = new RateLimiter(
                rateLimitProperties.getMaxRequests(),
                rateLimitProperties.getWindowSeconds()
        );
    }

    /**
     * Check if login request from IP is allowed.
     * @param ipAddress Client IP address
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkLoginRateLimit(String ipAddress) {
        if (!loginRateLimiter.allow(ipAddress)) {
            long resetSeconds = loginRateLimiter.getResetSeconds(ipAddress);
            throw new RateLimitExceededException(resetSeconds);
        }
    }

    /**
     * Cleanup expired rate limit buckets (call from scheduled task).
     */
    public void cleanup() {
        loginRateLimiter.cleanup();
    }
}
