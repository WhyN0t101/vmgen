package io.vmgen.api.dto.script;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

/**
 * Request payload for adding a conflict relationship between two scripts.
 */
@Data
public class AddScriptConflictRequest {

    @NotNull
    private UUID conflictsWithScriptId;
}

