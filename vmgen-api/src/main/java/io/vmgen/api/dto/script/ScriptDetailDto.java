package io.vmgen.api.dto.script;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Detailed representation of a script for admin views and editing.
 */
@Data
@Builder
public class ScriptDetailDto {
    private UUID id;
    private String identifier;
    private String version;
    private String name;
    private String description;
    private String difficultyLevel;
    private Boolean isActive;
    private UUID categoryId;
    private String categoryName;
    private String categoryColor;
    private String owaspMapping;
    private String scriptContent;
    private String flagLocation;
    private String flagOwnership;
    private String flagPermissions;
    private Instant createdAt;
    private String createdByUsername;
    private Instant deprecatedAt;

    // Requirements (for now, exposed as simple lists/values)
    private List<String> requiredOsFamilies;
    private List<String> requiredCapabilities;
    private Integer minRamMb;
    private Integer minDiskGb;
}

