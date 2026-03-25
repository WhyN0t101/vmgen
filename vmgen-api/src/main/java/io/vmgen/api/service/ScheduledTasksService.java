package io.vmgen.api.service;

import java.time.Duration;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTasksService {
    private final SessionService sessionService;
    private final RateLimitService rateLimitService;
    private final WorkerService workerService;

    private static final Duration IDLE_TIMEOUT = Duration.ofHours(24);

    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void cleanupSessions() {
        try {
            sessionService.cleanupExpiredAndIdle(IDLE_TIMEOUT);
        } catch (Exception e) {
            log.error("Scheduled cleanupSessions failed", e);
        }
    }

    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void cleanupRateLimits() {
        try{
            rateLimitService.cleanup();

        }catch(Exception e){
            log.error("Scheduled cleanupRateLimits failed", e);
        }
    }

    @Scheduled(fixedDelay = 60_000) // Every 1 minute
    public void detectStaleBuilds() {
        try{
            workerService.markStaleJobsAsFailed(Duration.ofMinutes(10));

        }catch(Exception e){
            log.error("Scheduled detectStaleBuilds failed", e);
        }
    }

    @Scheduled(fixedDelay = 300_000) // Every 5 minutes
    public void detectLongRunningBuilds() {
        try {
            workerService.markLongRunningJobsAsFailed(Duration.ofHours(3));
        } catch (Exception e) {
            log.error("Scheduled detectLongRunningBuilds failed", e);
        }
    }

    @Scheduled(fixedDelay = 180_000) // Every 3 minutes (3 × 60s heartbeat interval)
    public void detectStaleWorkers() {
        try {
            List<String> stale = workerService.getStaleWorkers(Duration.ofSeconds(180));
            if (!stale.isEmpty()) {
                log.warn("Workers have not sent a heartbeat in 180s: {}", stale);
            }
        } catch (Exception e) {
            log.error("Scheduled detectStaleWorkers failed", e);
        }
    }
}


