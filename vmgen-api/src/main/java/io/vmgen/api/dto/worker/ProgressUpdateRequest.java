package io.vmgen.api.dto.worker;

import lombok.Data;

/**
 * Worker-to-backend progress update for an in-flight VM request,
 * including stage, percentage, and a human-readable message.
 */
@Data
public class ProgressUpdateRequest {
    private String workerId;
    private String stage;      // "cloning", "injecting", "exporting"
    private Integer percent;    // 0-100
    private String message;     // Human-readable progress message
}
