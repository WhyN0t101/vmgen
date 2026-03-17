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
        private String workerId;
        private String templateIdentifier;
        private String templateVersion;
        private String templateSha256;
        private String buildStartedAt;
        private String buildFinishedAt;
        private String flagDerivationTimestamp;
        @Valid
        private List<ScriptRecord> scripts;
    }

    @Data
    public static class ScriptRecord {
        private String scriptId;
        private String identifier;
        private String version;
        private String sha256;
        private Integer executionOrder;
        private Integer exitCode;
        private Integer durationSeconds;
    }

    @Data
    public static class FlagInfo {
        private String scriptId;
        private String flagValueHash;  // SHA256 of actual flag
        private String placementLocation;
    }
}
