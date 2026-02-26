package io.vmgen.api.dto.category;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Summary projection of a script used when showing scripts for a category.
 */
@Data
@Builder
public class ScriptSummaryDto {
    private UUID id;
    private String identifier;
    private String version;
    private String name;
    private String difficultyLevel;
    private Boolean isActive;
}

