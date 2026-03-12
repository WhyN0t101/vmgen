package io.vmgen.api.dto.template;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.vmgen.api.models.template.TemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Template representation returned to regular (non-admin) users.
 * Infra-sensitive fields (Proxmox IDs, SSH config, validation logs) are excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTemplateResponse {

    private UUID id;
    private String identifier;
    private String version;
    private String osFamily;
    private String osVersion;
    private String architecture;
    private String description;
    private String sha256Hash;
    private Integer diskSizeGb;
    private Integer minRamMb;
    private Integer minCpuCores;
    private TemplateStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant validatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastValidatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant deprecatedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;

    private List<String> capabilities;
}
