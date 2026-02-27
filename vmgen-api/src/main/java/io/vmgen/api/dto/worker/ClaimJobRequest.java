package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request from a worker to claim the next available job in the queue.
 */
@Data
public class ClaimJobRequest {
    @NotBlank
    private String workerId;
}
