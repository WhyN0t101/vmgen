package io.vmgen.api.dto.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

/**
 * Wrapper for Proxmox VM list responses. Only the {@code data} field is
 * mapped; other fields are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxVmListResponse {
    private List<ProxmoxVmData> data;
}
