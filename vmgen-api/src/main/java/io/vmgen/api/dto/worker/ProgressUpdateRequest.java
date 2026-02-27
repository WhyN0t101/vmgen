package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Worker-to-backend progress update for an in-flight VM request,
 * including stage, percentage, and a human-readable message.
 */
@Data
public class ProgressUpdateRequest {
    @NotBlank
    private String workerId;
    @NotBlank
    private String stage;      // "cloning", "injecting", "exporting"
    @Min(0)
    @Max(100)
    private Integer percent;    // 0-100
    private String message;     // Human-readable progress message
}
