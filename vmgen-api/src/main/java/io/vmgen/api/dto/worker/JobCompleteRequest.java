package io.vmgen.api.dto.worker;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class JobCompleteRequest {
    private String workerId;
    private String ovaPath;
    private String ovaChecksum;  // SHA256
    private Long ovaSizeBytes;
    private Provenance provenance;
    private List<FlagInfo> flags;
    
    @Data
    public static class Provenance {
        private String templateIdentifier;
        private String templateVersion;
        private String templateSha256;
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
