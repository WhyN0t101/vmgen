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
import org.springframework.http.HttpStatus;
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
            
            return vmList.stream()
                .map(this::mapToVmSummary)
                .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.error("Failed to list VMs on node {}: {}", node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to list VMs: " + e.getMessage(), e);
        }
    }
    
    private ProxmoxVmDetails mapToVmDetails(ProxmoxVmConfig vmConfig) {
        ProxmoxVmDetails details = new ProxmoxVmDetails();
        details.setName(vmConfig.getName());
        details.setMemory(vmConfig.getMemory() != null ? vmConfig.getMemory() / 1024 / 1024 : null); // Convert to MB
        details.setCores(vmConfig.getCores());
        
        // Extract disk size from scsi0 or ide0 config (simplified - actual parsing would be more complex)
        // For now, set a placeholder
        details.setDiskSizeGb(null);
        
        return details;
    }
    
    private ProxmoxVmSummary mapToVmSummary(ProxmoxVmData vm) {
        ProxmoxVmSummary summary = new ProxmoxVmSummary();
        summary.setVmid(vm.getVmid());
        summary.setName(vm.getName());
        summary.setStatus(vm.getStatus());
        summary.setCpus(vm.getCpus());
        summary.setMaxmem(vm.getMaxmem());
        return summary;
    }
}
