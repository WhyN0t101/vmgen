package io.vmgen.api.dto.build;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BuildScriptDto {
    private UUID scriptId;
    private String scriptName;
    private int executionOrder;
}
