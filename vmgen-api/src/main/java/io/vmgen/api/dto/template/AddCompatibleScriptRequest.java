package io.vmgen.api.dto.template;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class AddCompatibleScriptRequest {

    @NotNull(message = "scriptId is required")
    private UUID scriptId;
}
