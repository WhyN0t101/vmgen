package io.vmgen.api.service;

import io.vmgen.api.dto.admin.WorkerStatusDTO;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMetricsService {

    private final WorkerService workerService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${vmgen.ova.storage-dir:/var/lib/vmgen/exports}")
    private String ovaStorageDir;

    /**
     * Number of jobs currently waiting to be claimed by a worker.
     */
    public int getQueueDepth() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vm_requests WHERE state = 'QUEUED'",
                Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Average time (in minutes) between job submission and worker claim,
     * across all jobs that have been claimed at least once.
     */
    public double getAvgWaitMinutes() {
        Double avg = jdbcTemplate.queryForObject(
                "SELECT AVG(EXTRACT(EPOCH FROM (claimed_at - submitted_at)) / 60)" +
                " FROM vm_requests WHERE state != 'QUEUED' AND claimed_at IS NOT NULL",
                Double.class);
        return avg != null ? avg : 0.0;
    }

    /**
     * Number of jobs that reached READY state in the last 24 hours.
     */
    public int getCompletionRate24h() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vm_requests" +
                " WHERE state = 'READY' AND completed_at > NOW() - INTERVAL '24 hours'",
                Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Current status of every worker that has ever sent a heartbeat since the last
     * backend restart, keyed by the most recent heartbeat payload.
     */
    public List<WorkerStatusDTO> getWorkerStatus() {
        return workerService.getAllWorkerHeartbeats().values().stream()
                .map(state -> WorkerStatusDTO.builder()
                        .workerId(state.workerId())
                        .activeJobIds(state.activeJobIds())
                        .cpuPercent(state.cpuPercent())
                        .memoryPercent(state.memoryPercent())
                        .lastSeenAt(state.lastSeenAt())
                        .build())
                .toList();
    }

    /**
     * Returns "UP" if the database responds to a ping within the connection timeout,
     * or "DOWN" if it does not.
     */
    public String getDbStatus() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (DataAccessException e) {
            log.warn("Database health check failed", e);
            return "DOWN";
        }
    }

    /**
     * Total bytes on the OVA storage filesystem.
     * Returns -1 if the path is inaccessible (e.g. not yet mounted in development).
     */
    public long getStorageTotalBytes() {
        return storageMetric(true);
    }

    /**
     * Usable bytes remaining on the OVA storage filesystem.
     * Returns -1 if the path is inaccessible.
     */
    public long getStorageUsableBytes() {
        return storageMetric(false);
    }

    /**
     * Authentication is embedded in the API; if this endpoint responds, auth is operational.
     */
    public String getAuthStatus() {
        return "UP";
    }

    private long storageMetric(boolean total) {
        try {
            Path path = Paths.get(ovaStorageDir);
            if (!Files.exists(path)) {
                return -1L;
            }
            FileStore store = Files.getFileStore(path);
            return total ? store.getTotalSpace() : store.getUsableSpace();
        } catch (IOException e) {
            log.warn("Could not read storage metrics for {}: {}", ovaStorageDir, e.getMessage());
            return -1L;
        }
    }
}
