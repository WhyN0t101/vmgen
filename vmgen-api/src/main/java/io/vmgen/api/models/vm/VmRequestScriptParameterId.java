package io.vmgen.api.models.vm;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Composite identifier for {@link VmRequestScriptParameter}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VmRequestScriptParameterId implements Serializable {

    private UUID vmRequest;
    private UUID script;
    private String parameterName;
}

