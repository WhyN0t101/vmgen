package io.vmgen.api.dto.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Worker-to-backend notification that a VM build has completed,
 * including OVA metadata, provenance information, and generated flags.
 */
@Data
public class JobCompleteRequest {
    @NotBlank
    private String workerId;
    @NotBlank
    private String ovaPath;
    @NotBlank
    private String ovaChecksum;  // SHA256
    private Long ovaSizeBytes;
    @Valid
    private Provenance provenance;
    @Valid
    private List<FlagInfo> flags;
    
    @Data
    public static class Provenance {
        private String templateIdentifier;
        private String templateVersion;
        private String templateSha256;
        @Valid
        private List<ScriptRecord> scripts;
        private String buildSeed;
        private String flagDerivationTimestamp;
        private String ovaSha256;
    }
    
    @Data
    public static class ScriptRecord {
        private String identifier;
        private String version;
        private String sha256;
        private Integer executionOrder;
    }
    
    @Data
    public static class FlagInfo {
        private String scriptId;
        private String flagValueHash;  // SHA256 of actual flag
        private String placementLocation;
    }
}
