package io.vmgen.api.dto.script;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Simple definition of a choice value when creating or updating a parameter.
 */
@Data
public class ScriptParameterChoiceDefinition {

    @NotBlank
    private String value;

    @NotBlank
    private String label;

    private String description;

    private Integer displayOrder = 0;
}

