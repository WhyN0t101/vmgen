package io.vmgen.api.models.script;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for {@link ScriptDependency}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptDependencyId implements Serializable {

    private UUID scriptId;
    private UUID requiresScriptId;
}

