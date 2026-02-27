package io.vmgen.api.dto.script;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight projection of a script for list views.
 */
@Data
@Builder
public class ScriptListDto {
    private UUID id;
    private String identifier;
    private String version;
    private String name;
    private String difficultyLevel;
    private Boolean isActive;
    private UUID categoryId;
    private String categoryName;
    private String categoryColor;
    private String owaspMapping;
    private Instant createdAt;
    /** IDs of scripts this script conflicts with (within the current compatible set). Null when not enriched. */
    private List<UUID> conflictsWithIds;
    /** IDs of scripts this script requires as dependencies. Null when not enriched. */
    private List<UUID> requiresIds;
}

