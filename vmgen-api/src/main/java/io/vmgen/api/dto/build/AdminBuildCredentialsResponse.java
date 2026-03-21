package io.vmgen.api.dto.build;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBuildCredentialsResponse {
    private UUID buildId;
    private String sshUsername;
    private String vmLoginPassword;
}
