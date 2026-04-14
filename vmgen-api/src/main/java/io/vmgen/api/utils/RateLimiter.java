package io.vmgen.api.utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter using token bucket algorithm.
 * For production, consider using Redis-based rate limiting for distributed systems.
 */
public class RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowSeconds;

    public RateLimiter(int maxRequests, long windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Check if a request from the given key should be allowed.
     * @param key Unique identifier (e.g., IP address)
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allow(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        return bucket.tryConsume();
    }

    /**
     * Get time until rate limit resets (in seconds).
     */
    public long getResetSeconds(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0;
        }
        return bucket.getResetSeconds();
    }

    /**
     * Clean up expired buckets periodically (call from scheduled task).
     */
    public void cleanup() {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private class Bucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile Instant windowStart;

        Bucket() {
            this.windowStart = Instant.now();
        }

        synchronized boolean tryConsume() {
            Instant now = Instant.now();
            long elapsedMs = ChronoUnit.MILLIS.between(windowStart, now);

            // Reset window if expired
            if (elapsedMs >= windowSeconds * 1000L) {
                count.set(0);
                windowStart = now;
            }

            // Check if limit exceeded
            if (count.get() >= maxRequests) {
                return false;
            }

            // Consume one token
            count.incrementAndGet();
            return true;
        }

        synchronized int getRemaining() {
            Instant now = Instant.now();
            long elapsedMs = ChronoUnit.MILLIS.between(windowStart, now);

            if (elapsedMs >= windowSeconds * 1000L) {
                return maxRequests;
            }

            return Math.max(0, maxRequests - count.get());
        }

        synchronized long getResetSeconds() {
            Instant now = Instant.now();
            long elapsedMs = ChronoUnit.MILLIS.between(windowStart, now);

            if (elapsedMs >= windowSeconds * 1000L) {
                return 0;
            }

            return (windowSeconds * 1000L - elapsedMs) / 1000L;
        }

        boolean isExpired(Instant now) {
            long elapsedMs = ChronoUnit.MILLIS.between(windowStart, now);
            return elapsedMs >= windowSeconds * 1000L;
        }
    }
}
