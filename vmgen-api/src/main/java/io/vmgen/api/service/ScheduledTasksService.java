package io.vmgen.api.service;

import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ScheduledTasksService {
    private final SessionService sessionService;

    // TODO: make configurable
    private static final Duration IDLE_TIMEOUT = Duration.ofHours(24);

    public ScheduledTasksService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Scheduled(fixedDelay = 300_000)
    public void cleanupSessions() {
        sessionService.cleanupExpiredAndIdle(IDLE_TIMEOUT);
    }
}


