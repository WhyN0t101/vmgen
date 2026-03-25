package io.vmgen.api.dto.admin;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Per-worker status snapshot returned by GET /api/admin/metrics.
 */
@Data
@Builder
public class WorkerStatusDTO {
    private String workerId;
    private List<String> activeJobIds;
    /** CPU utilisation (0–100) as reported by the worker host. Null if not supplied. */
    private Double cpuPercent;
    /** Virtual-memory utilisation (0–100) as reported by the worker host. Null if not supplied. */
    private Double memoryPercent;
    private Instant lastSeenAt;
}
