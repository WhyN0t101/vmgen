package io.vmgen.api.dto.script;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Result of resolving a set of selected scripts against the compatibility rules.
 *
 * <p>Contains the final ordered list of script IDs to execute and which ones
 * were auto-added due to dependency resolution.</p>
 */
@Data
@Builder
public class ScriptSelectionResult {

    private List<UUID> orderedScriptIds;
    private List<UUID> autoAddedScriptIds;
}

