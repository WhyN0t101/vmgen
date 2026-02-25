package io.vmgen.api.dto.proxmox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * High-level summary of a Proxmox VM or template, used to populate
 * admin-facing selection lists in the frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxVmSummary {
    private Integer vmid;
    private String name;
    private String status;
    private Integer cpus;
    private Long maxmem;
    private Boolean isTemplate;  // true if this is a Proxmox template
}
