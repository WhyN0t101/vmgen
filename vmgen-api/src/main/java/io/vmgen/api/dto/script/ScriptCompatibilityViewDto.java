package io.vmgen.api.dto.script;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * API-facing view of compatibility information for a script, including
 * human-friendly summaries of related scripts.
 */
@Data
@Builder
public class ScriptCompatibilityViewDto {

    private UUID scriptId;
    private List<ScriptSummaryDto> conflictsWith;
    private List<ScriptSummaryDto> requires;
}

