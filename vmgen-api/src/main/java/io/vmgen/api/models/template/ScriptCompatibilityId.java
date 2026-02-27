package io.vmgen.api.models.template;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCompatibilityId implements Serializable {
    private UUID scriptId;
    private UUID templateId;
}
