package io.vmgen.api.dto.script;

import io.vmgen.api.models.script.ParameterType;
import io.vmgen.api.models.script.RandomConfig;
import io.vmgen.api.models.script.ValidationRules;
import io.vmgen.api.models.script.ValueSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Data;

/**
 * Admin-side request payload for creating a new parameter on a script.
 */
@Data
public class CreateScriptParameterRequest {

    @NotBlank
    @Pattern(regexp = "^[A-Z_][A-Z0-9_]*$", message = "Must be uppercase letters, digits, and underscores")
    private String parameterName;

    @NotBlank
    private String displayName;

    private String description;

    @NotNull
    private ParameterType parameterType;

    @NotNull
    private ValueSource valueSource;

    private String defaultValue;

    private Boolean isRequired = Boolean.TRUE;

    private ValidationRules validationRules;

    private RandomConfig randomConfig;

    private Boolean userCanOverride = Boolean.FALSE;

    private Integer displayOrder = 0;

    private List<ScriptParameterChoiceDefinition> choices;
}

