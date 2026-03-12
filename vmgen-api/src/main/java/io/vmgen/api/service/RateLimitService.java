package io.vmgen.api.service;

import io.vmgen.api.config.RateLimitProperties;
import io.vmgen.api.exceptions.RateLimitExceededException;
import io.vmgen.api.utils.RateLimiter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private final RateLimiter loginRateLimiter;
    private final RateLimiter registerRateLimiter;

    public RateLimitService(RateLimitProperties rateLimitProperties) {
        this.loginRateLimiter = new RateLimiter(
                rateLimitProperties.getMaxRequests(),
                rateLimitProperties.getWindowSeconds()
        );
        this.registerRateLimiter = new RateLimiter(
                rateLimitProperties.getMaxRequests(),
                rateLimitProperties.getWindowSeconds()
        );
    }

    /**
     * Check if login request from IP is allowed.
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkLoginRateLimit(String ipAddress) {
        if (!loginRateLimiter.allow(ipAddress)) {
            long resetSeconds = loginRateLimiter.getResetSeconds(ipAddress);
            throw new RateLimitExceededException(resetSeconds);
        }
    }

    /**
     * Check if registration request from IP is allowed.
     * Uses an independent bucket from login to avoid cross-contamination.
     * @throws RateLimitExceededException if rate limit exceeded
     */
    public void checkRegisterRateLimit(String ipAddress) {
        if (!registerRateLimiter.allow(ipAddress)) {
            long resetSeconds = registerRateLimiter.getResetSeconds(ipAddress);
            throw new RateLimitExceededException(resetSeconds);
        }
    }

    /**
     * Cleanup expired rate limit buckets. Runs every 10 minutes via the Spring scheduler.
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanup() {
        loginRateLimiter.cleanup();
        registerRateLimiter.cleanup();
    }
}
