package io.vmgen.api.dto.script;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * DTO describing a single choice option for a script parameter.
 */
@Data
@Builder
public class ParameterChoiceDto {

    private UUID id;
    private String value;
    private String label;
    private String description;
    private Integer displayOrder;
}

