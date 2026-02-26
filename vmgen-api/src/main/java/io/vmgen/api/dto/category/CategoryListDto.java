package io.vmgen.api.dto.category;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight projection of a vulnerability category for list views.
 */
@Data
@Builder
public class CategoryListDto {
    private UUID id;
    private String identifier;
    private String name;
    private String color;
    private String icon;
    private String owaspMapping;
    private Integer displayOrder;
    private Integer scriptCount;
}

