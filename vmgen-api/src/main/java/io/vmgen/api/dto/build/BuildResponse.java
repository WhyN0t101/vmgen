package io.vmgen.api.dto.build;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildResponse {

    private UUID id;

    /** Lowercase state name: queued, building, exporting, ready, failed, archived. */
    private String state;

    private UUID templateId;
    private String templateName;

    private List<BuildScriptDto> scripts;

    private String difficulty;
    private boolean telemetryEnabled;

    /** Current worker stage description (null when queued or complete). */
    private String currentWorkerStage;

    private Instant submittedAt;
    private Instant claimedAt;
    private Instant buildingStartedAt;
    private Instant exportingStartedAt;
    private Instant completedAt;
    private Instant failedAt;
    private Instant archivedAt;
    private String failureReason;

    /** Estimated completion time for in-progress builds (heuristic). */
    private Instant estimatedCompletionAt;

    /** OVA artifact fields — populated once state is READY. */
    private String ovaFilename;
    private String ovaSha256;
    private Long ovaSizeBytes;
}
