package io.vmgen.api.dto.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Subset of the Proxmox VM configuration used by VMGen for validation
 * and display purposes.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVmConfig {
    private String name;
    private Integer memory;
    private Integer cores;
    private Integer sockets;
    private String scsi0;  // Disk config
    private String ide0;
    // Add other fields as needed
}
