package io.vmgen.api.dto.template;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateResponse {
    
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
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant validatedAt;
    
    private UUID validatedBy;
    
    private String validationNotes;
    private UUID createdBy;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant deprecatedAt;
    
    // V3 fields
    private String status;
    private Integer proxmoxVmid;
    private String proxmoxNode;
    private String diskStorage;
    private String validationLogs; // JSON string
    private Instant lastValidatedAt;
    private String expectedSshUser;
    private Integer expectedSshPort;
    private String networkBridge;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    
    private List<String> capabilities;
}
