package io.vmgen.api.dto.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Raw VM entry as returned by the Proxmox VM list API. Used as an
 * intermediate representation before mapping to higher-level summaries.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVmData {
    private Integer vmid;
    private String name;
    private String status;  // running, stopped
    private Integer cpus;
    private Long maxmem;    // Bytes
    private Long maxdisk;   // Bytes
    private Integer template;  // 1 if this is a Proxmox template, 0 or null if regular VM
}
