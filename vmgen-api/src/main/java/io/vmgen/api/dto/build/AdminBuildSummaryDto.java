package io.vmgen.api.dto.build;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBuildSummaryDto {
    private UUID id;
    private String state;
    private UUID userId;
    private String username;
    private UUID templateId;
    private String templateName;
    private String difficulty;
    private String currentWorkerStage;
    private String failureReason;
    private String ovaFilename;
    private String ovaSha256;
    private Long ovaSizeBytes;
    private Instant submittedAt;
    private Instant completedAt;
    private Instant failedAt;
}
