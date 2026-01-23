package io.vmgen.api.dto.proxmox;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxmoxVmDetails {
    private String name;
    private Integer memory;  // MB
    private Integer cores;
    private Integer diskSizeGb;
}
