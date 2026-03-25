package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeriveFlagsRequest {
    @NotBlank
    private String workerId;
    @NotBlank
    private String flagDerivationTimestamp;
}
