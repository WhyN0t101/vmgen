package io.vmgen.api.dto.worker;

import lombok.Data;

/**
 * Request from a worker to claim the next available job in the queue.
 */
@Data
public class ClaimJobRequest {
    private String workerId;
}
