package io.vmgen.api.dto.worker;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

/**
 * Periodic liveness signal from a worker.
 * Sent every 60 seconds regardless of build activity.
 */
@Data
public class HeartbeatRequest {
    @NotBlank
    private String workerId;
    private List<String> activeJobIds;
}
