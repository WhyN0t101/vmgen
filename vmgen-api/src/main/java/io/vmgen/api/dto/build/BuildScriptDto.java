package io.vmgen.api.dto.build;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildScriptDto {
    private UUID scriptId;
    private String scriptName;
    private int executionOrder;
    /** Non-null when the correct flag for this script has been submitted. */
    private Instant flagSolvedAt;
}
