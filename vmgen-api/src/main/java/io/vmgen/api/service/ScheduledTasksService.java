package io.vmgen.api.service;

import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTasksService {
    private final SessionService sessionService;
    private final RateLimitService rateLimitService;

    // TODO: make configurable
    private static final Duration IDLE_TIMEOUT = Duration.ofHours(24);

    public ScheduledTasksService(SessionService sessionService, RateLimitService rateLimitService) {
        this.sessionService = sessionService;
        this.rateLimitService = rateLimitService;
    }

    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void cleanupSessions() {
        sessionService.cleanupExpiredAndIdle(IDLE_TIMEOUT);
    }

    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void cleanupRateLimits() {
        rateLimitService.cleanup();
    }
}


