package io.vmgen.api.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request payload for updating an existing vulnerability category.
 */
@Data
public class UpdateCategoryRequest {

    @NotBlank
    @Pattern(regexp = "^[a-z0-9-]+$", message = "Identifier must be lowercase letters, numbers, and hyphens only")
    @Size(max = 100)
    private String identifier;

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Color must be hex format (#RRGGBB)")
    private String color;

    @Size(max = 50)
    private String icon;

    @Size(max = 50)
    private String owaspMapping;

    @Size(max = 20)
    private String severityRange;

    private Integer displayOrder;
}

