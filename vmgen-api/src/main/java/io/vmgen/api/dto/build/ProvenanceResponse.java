package io.vmgen.api.dto.build;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProvenanceResponse {
    private UUID buildId;
    private String templateIdentifier;
    private String templateVersion;
    private String templateSha256;
    private String buildSeed;
    private String difficultyLevel;
    private boolean telemetryEnabled;
    private String ovaSha256;
    private Instant flagDerivationTimestamp;
    private Instant finalisedAt;
    private String workerId;
    private Instant buildStartedAt;
    private Instant buildFinishedAt;
    private List<ScriptInfo> scripts;
    private List<ParameterInfo> parameters;
    private List<DependencyInfo> dependencies;

    @Data
    @Builder
    public static class ParameterInfo {
        private String name;
        private String value;
    }

    @Data
    @Builder
    public static class DependencyInfo {
        private String type;
        private String name;
        private String version;
        private String sourceUrl;
        private String sha256;
        private Instant retrievedAt;
    }

    @Data
    @Builder
    public static class ScriptInfo {
        private String scriptId;
        private String identifier;
        private String version;
        private String sha256;
        private int executionOrder;
        private Integer exitCode;
        private Integer durationSeconds;
    }
}
