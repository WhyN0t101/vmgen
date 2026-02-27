package io.vmgen.api.dto.script;

import io.vmgen.api.models.script.ParameterType;
import io.vmgen.api.models.script.RandomConfig;
import io.vmgen.api.models.script.ValidationRules;
import io.vmgen.api.models.script.ValueSource;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO returned to admins when inspecting or editing script parameters.
 */
@Data
@Builder
public class ScriptParameterDto {

    private UUID id;
    private String parameterName;
    private String displayName;
    private String description;
    private ParameterType parameterType;
    private ValueSource valueSource;
    private String defaultValue;
    private Boolean isRequired;
    private ValidationRules validationRules;
    private RandomConfig randomConfig;
    private Boolean userCanOverride;
    private Integer displayOrder;
    private List<ParameterChoiceDto> choices;
}

