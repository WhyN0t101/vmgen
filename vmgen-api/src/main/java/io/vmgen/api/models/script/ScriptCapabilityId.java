package io.vmgen.api.models.script;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptCapabilityId implements Serializable {

    private UUID scriptId;
    private String capability;
}
