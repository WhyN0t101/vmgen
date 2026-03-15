package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Worker-to-backend request to append a single build log entry for an in-flight job.
 * Log writes are best-effort: backend accepts them regardless of current job state so
 * that error entries emitted just before a fail_job call are not rejected.
 */
@Data
public class AppendBuildLogRequest {

    @NotBlank
    private String workerId;

    @NotBlank
    @Pattern(regexp = "INFO|WARN|ERROR", message = "logLevel must be INFO, WARN, or ERROR")
    private String logLevel;

    /** Pipeline stage the entry belongs to — nullable for cross-stage messages. */
    @Size(max = 100)
    private String stage;

    @NotBlank
    @Size(max = 10_000)
    private String message;

    /**
     * ISO-8601 timestamp from the worker clock.
     * When present and parseable it is stored as-is; otherwise {@code @PrePersist} falls
     * back to {@code Instant.now()} on the backend side.
     */
    private String loggedAt;
}
