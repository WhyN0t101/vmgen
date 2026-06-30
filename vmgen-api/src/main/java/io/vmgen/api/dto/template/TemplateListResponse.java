package io.vmgen.api.dto.template;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.vmgen.api.models.template.TemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight view of a template for list endpoints. Carries the catalogue-card
 * fields (architecture and declared capabilities) but omits heavyweight detail
 * such as Proxmox config and validation logs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateListResponse {

    private UUID id;
    private String identifier;
    private String version;
    private String osFamily;
    private String architecture;
    private String description;
    private TemplateStatus status;
    private List<String> capabilities;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
