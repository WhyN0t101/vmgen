package io.vmgen.api.dto.worker;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
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
    private UUID templateId;
    private String templateIdentifier;
    private String templateVersion;
    private String templateSha256;
    private Integer proxmoxVmid;
    private String proxmoxNode;
    
    // Scripts to apply
    private List<ScriptInfo> scripts;
    
    // SSH configuration (needed by worker to connect to the VM guest)
    private String sshAuthMethod;     // "cloudinit" | "password_reset" | "static_key"
    private String expectedSshUser;
    private Integer expectedSshPort;
    private String sshPassword;       // non-null when sshAuthMethod = "password_reset"
    private String sshPrivateKey;     // non-null when sshAuthMethod = "static_key" (full PEM content)
    private String diskStorage;       // Proxmox storage name — used for OVA export

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
        private String flagOwnership;
        private String flagPermissions;
        private Integer executionOrder;
        private String scriptContent;
        private Map<String, String> parameterValues;
        private Integer minRamMb;
        private Integer minDiskGb;
    }
}
