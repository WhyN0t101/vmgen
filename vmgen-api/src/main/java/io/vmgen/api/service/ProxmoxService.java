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
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxmoxService {

    private static final Pattern DISK_CONFIG_PATTERN = Pattern.compile("^([^:]+):.*?(?:,size=(\\d+)G)?");

    private final RestTemplate proxmoxRestTemplate;
    private final ProxmoxApiConfig proxmoxConfig;
    private final ObjectMapper objectMapper;

    /**
     * Check if a Proxmox VM exists.
     */
    public boolean vmExists(Integer vmid, String node) {
        if (vmid == null || node == null || node.isBlank()) {
            log.warn("vmExists called with invalid parameters: vmid={}, node={}", vmid, node);
            return false;
        }

        try {
            String url = buildUrl("/nodes/{node}/qemu/{vmid}/status/current");
            ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, node, vmid);

            boolean exists = response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
            log.debug("VM existence check: VMID {} on node {} - {}", vmid, node, exists ? "exists" : "not found");
            return exists;

        } catch (HttpClientErrorException.NotFound e) {
            log.debug("VM {} not found on node {}", vmid, node);
            return false;
        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to check VM existence for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to check VM existence: " + e.getMessage(), e);
        }
    }

    /**
     * Get VM details from Proxmox.
     */
    public ProxmoxVmDetails getVmDetails(Integer vmid, String node) {
        validateVmParameters(vmid, node);

        try {
            String url = buildUrl("/nodes/{node}/qemu/{vmid}/config");
            ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, node, vmid);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + node);
            }

            ProxmoxApiResponse apiResponse = response.getBody();
            ProxmoxVmConfig vmConfig = objectMapper.convertValue(apiResponse.getData(), ProxmoxVmConfig.class);

            ProxmoxVmDetails details = mapToVmDetails(vmConfig);
            log.info("Retrieved VM details for VMID {} on node {}: {}", vmid, node, details.getName());
            return details;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("VM {} not found on node {}", vmid, node);
            throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + node);
        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to get VM details for VMID {} on node {}: {}", vmid, node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to get VM details: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting VM details for VMID {} on node {}: {}", vmid, node, e.getMessage(), e);
            throw new ProxmoxConnectionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Get VM storage name from VM config.
     */
    public Optional<String> getVmStorage(Integer vmid, String node) {
        if (vmid == null || node == null || node.isBlank()) {
            log.warn("getVmStorage called with invalid parameters: vmid={}, node={}", vmid, node);
            return Optional.empty();
        }

        try {
            ProxmoxVmDetails details = getVmDetails(vmid, node);
            return Optional.ofNullable(details.getDiskStorage());
        } catch (ProxmoxVmNotFoundException e) {
            log.debug("VM {} not found on node {} when retrieving storage", vmid, node);
            return Optional.empty();
        } catch (ProxmoxConnectionException e) {
            log.warn("Could not determine storage for VM {} on node {}: {}", vmid, node, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * List all VMs on a Proxmox node.
     */
    public List<ProxmoxVmSummary> listVms(String node) {
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Node parameter is required");
        }

        try {
            String url = buildUrl("/nodes/{node}/qemu");
            ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, node);

            if (response.getBody() == null || response.getBody().getData() == null) {
                log.debug("No VMs found on node {}", node);
                return Collections.emptyList();
            }

            ProxmoxApiResponse apiResponse = response.getBody();
            List<ProxmoxVmData> vmList = objectMapper.convertValue(
                    apiResponse.getData(),
                    new TypeReference<>() {
                    }
            );

            if (vmList == null || vmList.isEmpty()) {
                log.debug("Empty VM list returned for node {}", node);
                return Collections.emptyList();
            }

            List<ProxmoxVmSummary> summaries = vmList.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapToVmSummary)
                    .collect(Collectors.toList());

            log.info("Listed {} VMs on node {}", summaries.size(), node);
            return summaries;

        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout listing VMs on node {}: {}", node, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to list VMs on node {}: {}", node, e.getMessage());
            throw new ProxmoxConnectionException("Failed to list VMs: " + e.getMessage(), e);
        }
    }

    private void validateVmParameters(Integer vmid, String node) {
        if (vmid == null) {
            throw new IllegalArgumentException("VMID must be provided");
        }
        if (node == null || node.isBlank()) {
            throw new IllegalArgumentException("Node must be provided");
        }
    }

    private String buildUrl(String path) {
        return UriComponentsBuilder
                .fromUriString(proxmoxConfig.getUrl())
                .path(path)
                .build()
                .toUriString();
    }

    private ProxmoxVmDetails mapToVmDetails(ProxmoxVmConfig vmConfig) {
        if (vmConfig == null) {
            throw new IllegalArgumentException("VM config cannot be null");
        }

        ProxmoxVmDetails details = new ProxmoxVmDetails();
        details.setName(vmConfig.getName());
        details.setMemory(vmConfig.getMemory());
        details.setCores(vmConfig.getCores());

        // Try scsi0 first, then ide0
        DiskInfo diskInfo = extractDiskInfo(vmConfig.getScsi0());
        if (diskInfo == null) {
            diskInfo = extractDiskInfo(vmConfig.getIde0());
        }
        // Remove the virtio0 line if it doesn't exist in your DTO

        if (diskInfo != null) {
            details.setDiskStorage(diskInfo.storage);
            details.setDiskSizeGb(diskInfo.sizeGb);
        }

        return details;
    }

    /**
     * Extract storage name and size from Proxmox disk config string.
     * Format: "storage:vm-100-disk-0,size=32G" or "storage:base-100-disk-0"
     */
    private DiskInfo extractDiskInfo(String diskConfig) {
        if (diskConfig == null || diskConfig.isBlank()) {
            return null;
        }

        Matcher matcher = DISK_CONFIG_PATTERN.matcher(diskConfig);
        if (matcher.matches()) {
            String storage = matcher.group(1);
            String sizeStr = matcher.group(2);
            Integer sizeGb = sizeStr != null ? Integer.parseInt(sizeStr) : null;
            return new DiskInfo(storage, sizeGb);
        }

        log.warn("Could not parse disk config: {}", diskConfig);
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
        summary.setIsTemplate(vm.getTemplate() != null && vm.getTemplate() == 1);
        return summary;
    }

    private record DiskInfo(String storage, Integer sizeGb) {}
}