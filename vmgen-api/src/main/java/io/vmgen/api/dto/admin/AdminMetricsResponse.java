package io.vmgen.api.dto.admin;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Response body for GET /api/admin/metrics.
 */
@Data
@Builder
public class AdminMetricsResponse {
    private int queueDepth;
    private double avgWaitMinutes;
    private int completionRate24h;
    private List<WorkerStatusDTO> workers;
    private String dbStatus;
    private long storageTotalBytes;
    private long storageUsableBytes;
    /** Authentication service health (embedded in API — UP when the application responds). */
    private String authStatus;
}
