package io.vmgen.api.dto.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

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
