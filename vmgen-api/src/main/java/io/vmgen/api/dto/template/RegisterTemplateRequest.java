package io.vmgen.api.dto.template;

import io.vmgen.api.config.TemplateDefaults;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * Payload for registering a new template from an existing Proxmox VM,
 * including OS metadata, resources, network settings, and capabilities.
 */
@Data
public class RegisterTemplateRequest {
    
    @NotBlank(message = "Identifier is required")
    @Size(max = 255, message = "Identifier must not exceed 255 characters")
    private String identifier;
    
    @NotBlank(message = "Version is required")
    @Size(max = 50, message = "Version must not exceed 50 characters")
    @Pattern(regexp = "^v?\\d+\\.\\d+\\.\\d+(-.*)?$", message = "Version must follow semantic versioning (e.g., v1.0.0 or 1.0.0)")
    private String version;
    
    @NotBlank(message = "OS family is required")
    @Size(max = 100, message = "OS family must not exceed 100 characters")
    @Pattern(regexp = "^(linux|windows)$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "OS family must be 'linux' or 'windows'")
    private String osFamily;
    
    @NotBlank(message = "OS version is required")
    @Size(max = 100, message = "OS version must not exceed 100 characters")
    private String osVersion;
    
    @NotBlank(message = "Architecture is required")
    @Size(max = 50, message = "Architecture must not exceed 50 characters")
    @Pattern(regexp = "^(x64|arm64)$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Architecture must be 'x64' or 'arm64'")
    private String architecture;
    
    private String description;
    
    // Proxmox fields
    @NotNull(message = "Proxmox VMID is required")
    @Min(value = 100, message = "Proxmox VMID must be at least 100")
    private Integer proxmoxVmid;
    
    @Size(max = 100, message = "Proxmox node must not exceed 100 characters")
    private String proxmoxNode;
    
    @Size(max = 100, message = "Disk storage must not exceed 100 characters")
    private String diskStorage;
    
    // Validation config
    @Size(max = 50, message = "Expected SSH user must not exceed 50 characters")
    private String expectedSshUser = TemplateDefaults.SSH_USER;
    
    @jakarta.validation.constraints.Max(value = 65535, message = "Expected SSH port must not exceed 65535")
    private Integer expectedSshPort = TemplateDefaults.SSH_PORT;
    
    @Size(max = 100, message = "Network bridge must not exceed 100 characters")
    private String networkBridge;
    
    /**
     * SSH authentication method used by the worker when connecting to this
     * template during validation and build: {@code cloudinit},
     * {@code password_reset}, or {@code static_key}.
     */
    @Size(max = 20, message = "SSH auth method must not exceed 20 characters")
    @Pattern(
            regexp = "^(cloudinit|password_reset|static_key)$",
            message = "sshAuthMethod must be one of: cloudinit, password_reset, static_key"
    )
    private String sshAuthMethod = "cloudinit";
    
    // Resources
    @NotNull(message = "Disk size is required")
    @Min(value = 1, message = "Disk size must be at least 1 GB")
    private Integer diskSizeGb;
    
    @Min(value = 512, message = "Minimum RAM must be at least 512 MB")
    private Integer minRamMb = 2048;
    
    @Min(value = 1, message = "Minimum CPU cores must be at least 1")
    private Integer minCpuCores = 2;
    
    // Capabilities
    private List<String> capabilities = List.of();
}
