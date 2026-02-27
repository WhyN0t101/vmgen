package io.vmgen.api.dto.script;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Internal compatibility graph projection for a single script.
 *
 * <p>This DTO is returned by the compatibility service and can be further
 * enriched for API responses.</p>
 */
@Data
@Builder
public class ScriptCompatibilityInfo {

    private UUID scriptId;
    private List<UUID> conflictsWith;
    private List<UUID> requires;
}

