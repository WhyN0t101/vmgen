package io.vmgen.api.dto.proxmox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxVmSummary {
    private Integer vmid;
    private String name;
    private String status;
    private Integer cpus;
    private Long maxmem;
}
