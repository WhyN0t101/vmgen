package io.vmgen.api.models.script;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRequirementId implements Serializable {

    private UUID scriptId;
    private String requirementType;
    private String requirementValue;
}
