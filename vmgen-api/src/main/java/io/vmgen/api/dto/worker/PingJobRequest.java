package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PingJobRequest {
    @NotBlank
    private String workerId;
}
