package io.vmgen.api.dto.category;

import io.vmgen.api.dto.category.ScriptSummaryDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Detailed view of a vulnerability category including associated scripts.
 */
@Data
@Builder
public class CategoryDetailDto {
    private UUID id;
    private String identifier;
    private String name;
    private String description;
    private String color;
    private String icon;
    private String owaspMapping;
    private String severityRange;
    private Boolean isActive;
    private Integer displayOrder;
    private Instant createdAt;
    private String createdByUsername;
    private Instant deprecatedAt;
    private Integer scriptCount;
    private List<ScriptSummaryDto> scripts;
}

