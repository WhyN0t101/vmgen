package io.vmgen.api.dto.worker;

import lombok.Data;

@Data
public class JobFailRequest {
    private String workerId;
    private String errorType;   // "script_failed", "timeout", "proxmox_error"
    private String message;
    private String logs;        // Truncated error logs
}
