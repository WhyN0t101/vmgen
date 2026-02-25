package io.vmgen.api.dto.proxmox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Generic wrapper for Proxmox API responses. Only the {@code data}
 * payload is mapped; other metadata fields are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxmoxApiResponse<T> {
    private T data;
}
