package io.vmgen.api.dto.template;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update payload for mutable template metadata such as description
 * and resource/SSH configuration.
 */
@Data
public class UpdateTemplateMetadataRequest {
    
    private String description;
    
    @Size(max = 50, message = "Expected SSH user must not exceed 50 characters")
    private String expectedSshUser;
    
    @Min(value = 1, message = "Expected SSH port must be at least 1")
    private Integer expectedSshPort;
    
    @Size(max = 100, message = "Network bridge must not exceed 100 characters")
    private String networkBridge;
    
    @Min(value = 512, message = "Minimum RAM must be at least 512 MB")
    private Integer minRamMb;
    
    @Min(value = 1, message = "Minimum CPU cores must be at least 1")
    private Integer minCpuCores;
}
