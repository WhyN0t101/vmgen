package io.vmgen.api.models.script;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for {@link ScriptConflict}.
 * Uses the source and target script UUIDs as primary key columns.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptConflictId implements Serializable {

    private UUID scriptId;
    private UUID conflictsWithScriptId;
}

