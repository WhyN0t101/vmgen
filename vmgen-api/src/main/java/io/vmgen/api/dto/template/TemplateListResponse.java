package io.vmgen.api.dto.template;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

import io.vmgen.api.models.TemplateStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateListResponse {
    
    private UUID id;
    private String identifier;
    private String version;
    private String osFamily;
    private TemplateStatus status;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
}
