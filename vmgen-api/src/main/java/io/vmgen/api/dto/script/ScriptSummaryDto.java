package io.vmgen.api.dto.script;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Lightweight summary of a script used when showing compatibility information.
 */
@Data
@Builder
public class ScriptSummaryDto {

    private UUID id;
    private String identifier;
    private String version;
    private String name;
}

