package io.vmgen.api.dto.worker;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Full job specification returned to a worker when it successfully
 * claims a VM build request from the backend.
 */
@Data
@Builder
public class ClaimJobResponse {
    private UUID requestId;
    
    // Template info
    private String templateIdentifier;
    private String templateVersion;
    private String templateSha256;
    private Integer proxmoxVmid;
    private String proxmoxNode;
    
    // Scripts to apply
    private List<ScriptInfo> scripts;
    
    // Build parameters
    private String buildSeed;
    private String difficultyLevel;
    private Boolean telemetryEnabled;
    
    @Data
    @Builder
    public static class ScriptInfo {
        private UUID scriptId;
        private String identifier;
        private String version;
        private String sha256;
        private String flagLocation;
        private Integer executionOrder;
    }
}
