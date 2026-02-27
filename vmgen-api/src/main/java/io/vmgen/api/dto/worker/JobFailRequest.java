package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Worker-to-backend notification that a VM build has failed, including
 * a coarse error type, message, and optional truncated logs.
 */
@Data
public class JobFailRequest {
    @NotBlank
    private String workerId;
    @NotBlank
    private String errorType;   // "script_failed", "timeout", "proxmox_error"
    private String message;
    private String logs;        // Truncated error logs
}
