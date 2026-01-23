package io.vmgen.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.config.ProxmoxApiConfig;
import io.vmgen.api.dto.proxmox.ProxmoxApiResponse;
import io.vmgen.api.dto.proxmox.ProxmoxVmConfig;
import io.vmgen.api.dto.proxmox.ProxmoxVmData;
import io.vmgen.api.dto.proxmox.ProxmoxVmDetails;
import io.vmgen.api.dto.proxmox.ProxmoxVmSummary;
import io.vmgen.api.exceptions.ProxmoxConnectionException;
import io.vmgen.api.exceptions.ProxmoxVmNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxmoxService {
    
    private final RestTemplate proxmoxRestTemplate;
    private final ProxmoxApiConfig proxmoxConfig;
    private final ObjectMapper objectMapper;
    
    /**
     * Check if a Proxmox VM exists.
     */
    public boolean vmExists(Integer vmid, String node) {
        if (vmid == null || node == null || node.isBlank()) {
            return false;
        }
        try {
            String url = proxmoxConfig.getUrl() + "/nodes/" + node + "/qemu/" + vmid + "/status/current";
            ResponseEntity<ProxmoxApiResponse> response = 
                proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class);
            return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException e) {
            log.error("Failed to check VM existence for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to check VM existence: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get VM details from Proxmox.
     */
    public ProxmoxVmDetails getVmDetails(Integer vmid, String node) {
        if (vmid == null || node == null || node.isBlank()) {
            throw new ProxmoxVmNotFoundException("VMID and node must be provided");
        }
        try {
            String url = proxmoxConfig.getUrl() + "/nodes/" + node + "/qemu/" + vmid + "/config";
            ResponseEntity<ProxmoxApiResponse> response = 
                proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + node);
            }
            
            // Parse the response
            ProxmoxApiResponse apiResponse = response.getBody();
            ProxmoxVmConfig vmConfig = objectMapper.convertValue(apiResponse.getData(), ProxmoxVmConfig.class);
            
            return mapToVmDetails(vmConfig);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + node);
        } catch (RestClientException e) {
            log.error("Failed to get VM details for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to get VM details: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get VM storage name from VM config.
     * This is useful for auto-populating disk storage when registering a template.
     */
    public String getVmStorage(Integer vmid, String node) {
        if (vmid == null || node == null || node.isBlank()) {
            return null;
        }
        try {
            ProxmoxVmDetails details = getVmDetails(vmid, node);
            return details.getDiskStorage();
        } catch (Exception e) {
            log.warn("Could not determine storage for VM {} on node {}: {}", vmid, node, e.getMessage());
            return null;
        }
    }
    
    /**
     * List all VMs on a Proxmox node.
     */
    public List<ProxmoxVmSummary> listVms(String node) {
        try {
            String url = proxmoxConfig.getUrl() + "/nodes/" + node + "/qemu";
            ResponseEntity<ProxmoxApiResponse> response = 
                proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class);
            
            if (response.getBody() == null || response.getBody().getData() == null) {
                return Collections.emptyList();
            }
            
            // Parse the response
            ProxmoxApiResponse apiResponse = response.getBody();
            List<ProxmoxVmData> vmList = objectMapper.convertValue(
                apiResponse.getData(), 
                new TypeReference<List<ProxmoxVmData>>() {}
            );
            
            if (vmList == null) {
                return Collections.emptyList();
            }
            
            return vmList.stream()
                .filter(vm -> vm != null)  // Filter out null entries
                .map(this::mapToVmSummary)
                .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.error("Failed to list VMs on node {}: {}", node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to list VMs: " + e.getMessage(), e);
        }
    }
    
    private ProxmoxVmDetails mapToVmDetails(ProxmoxVmConfig vmConfig) {
        if (vmConfig == null) {
            throw new IllegalArgumentException("VM config cannot be null");
        }
        ProxmoxVmDetails details = new ProxmoxVmDetails();
        details.setName(vmConfig.getName());
        details.setMemory(vmConfig.getMemory() != null ? vmConfig.getMemory() / 1024 / 1024 : null); // Convert to MB
        details.setCores(vmConfig.getCores());
        
        // Extract disk storage from scsi0 or ide0 config
        // Format: "storage:vm-100-disk-0,size=32G" or "storage:base-100-disk-0"
        String diskStorage = extractStorageFromDiskConfig(vmConfig.getScsi0());
        if (diskStorage == null) {
            diskStorage = extractStorageFromDiskConfig(vmConfig.getIde0());
        }
        details.setDiskStorage(diskStorage);
        
        // Extract disk size from scsi0 or ide0 config (simplified - actual parsing would be more complex)
        // For now, set a placeholder
        details.setDiskSizeGb(null);
        
        return details;
    }
    
    /**
     * Extract storage name from Proxmox disk config string.
     * Format: "storage:vm-100-disk-0,size=32G" or "storage:base-100-disk-0"
     * Returns the storage name (e.g., "data1", "local-lvm") or null if not found.
     */
    private String extractStorageFromDiskConfig(String diskConfig) {
        if (diskConfig == null || diskConfig.isBlank()) {
            return null;
        }
        // Extract storage name before the first colon
        int colonIndex = diskConfig.indexOf(':');
        if (colonIndex > 0) {
            return diskConfig.substring(0, colonIndex);
        }
        return null;
    }
    
    private ProxmoxVmSummary mapToVmSummary(ProxmoxVmData vm) {
        if (vm == null) {
            throw new IllegalArgumentException("VM data cannot be null");
        }
        ProxmoxVmSummary summary = new ProxmoxVmSummary();
        summary.setVmid(vm.getVmid());
        summary.setName(vm.getName());
        summary.setStatus(vm.getStatus());
        summary.setCpus(vm.getCpus());
        summary.setMaxmem(vm.getMaxmem());
        // Proxmox templates have template=1, regular VMs have template=0 or null
        summary.setIsTemplate(vm.getTemplate() != null && vm.getTemplate() == 1);
        return summary;
    }
}
