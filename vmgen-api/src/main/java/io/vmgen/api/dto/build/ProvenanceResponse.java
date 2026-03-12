package io.vmgen.api.dto.build;

import java.time.Instant;
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
}