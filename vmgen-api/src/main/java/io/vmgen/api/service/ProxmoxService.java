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
     * Checks whether a VM with the given VMID exists on the specified node.
     * Falls back to the configured default node if {@code node} is null or blank.
     * Returns {@code false} for 404 responses rather than throwing.
     *
     * @param vmid the VMID to check; must not be null
     * @param node the target Proxmox node; falls back to {@code proxmox.api.node} if null or blank
     * @return {@code true} if the VM exists and the API returns a 2xx response, {@code false} otherwise
     * @throws ProxmoxConnectionException if the request times out or a non-404 HTTP error occurs
     */
    public boolean vmExists(Integer vmid, String node) {
        if (vmid == null) {
            log.warn("vmExists called with null vmid");
            return false;
        }
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();

        try {
            String url = buildUrl("/nodes/{node}/qemu/{vmid}/status/current");
            @SuppressWarnings("rawtypes") ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, targetNode, vmid);

            boolean exists = response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
            log.debug("VM existence check: VMID {} on node {} - {}", vmid, targetNode, exists ? "exists" : "not found");
            return exists;

        } catch (HttpClientErrorException.NotFound e) {
            log.debug("VM {} not found on node {}", vmid, targetNode);
            return false;
        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout for VMID {} on node {}: {}", vmid, targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to check VM existence for VMID {} on node {}: {}", vmid, targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Failed to check VM existence: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the configuration details of a VM from Proxmox.
     * Falls back to the configured default node if {@code node} is null or blank.
     *
     * @param vmid the VMID to retrieve; must not be null
     * @param node the target Proxmox node; falls back to {@code proxmox.api.node} if null or blank
     * @return the VM configuration mapped to {@link ProxmoxVmDetails}
     * @throws IllegalArgumentException if {@code vmid} is null
     * @throws ProxmoxVmNotFoundException if the VM does not exist on the target node
     * @throws ProxmoxConnectionException if the request fails or times out
     */
    public ProxmoxVmDetails getVmDetails(Integer vmid, String node) {
        if (vmid == null) throw new IllegalArgumentException("VMID must be provided");
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();
        try {
            String url = buildUrl("/nodes/{node}/qemu/{vmid}/config");
            @SuppressWarnings("rawtypes") ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, targetNode, vmid);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + targetNode);
            }

            @SuppressWarnings("rawtypes") ProxmoxApiResponse apiResponse = response.getBody();
            ProxmoxVmConfig vmConfig = objectMapper.convertValue(apiResponse.getData(), ProxmoxVmConfig.class);

            ProxmoxVmDetails details = mapToVmDetails(vmConfig);
            log.info("Retrieved VM details for VMID {} on node {}: {}", vmid, targetNode, details.getName());
            return details;

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("VM {} not found on node {}", vmid, targetNode);
            throw new ProxmoxVmNotFoundException("VM " + vmid + " not found on node " + targetNode);
        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout for VMID {} on node {}: {}", vmid, targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to get VM details for VMID {} on node {}: {}", vmid, targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Failed to get VM details: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting VM details for VMID {} on node {}: {}", vmid, targetNode, e.getMessage(), e);
            throw new ProxmoxConnectionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the primary disk storage name for a given VM.
     * Falls back to the configured default node if {@code node} is null or blank.
     * Returns an empty {@link Optional} if the VM is not found, storage cannot be determined,
     * or a connection error occurs, rather than propagating exceptions.
     *
     * @param vmid the VMID to query; must not be null
     * @param node the target Proxmox node; falls back to {@code proxmox.api.node} if null or blank
     * @return an {@link Optional} containing the storage name, or empty if unavailable
     */

    public Optional<String> getVmStorage(Integer vmid, String node) {
        if (vmid == null) {
            log.warn("getVmStorage called with null vmid");
            return Optional.empty();
        }
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();
        try {
            ProxmoxVmDetails details = getVmDetails(vmid, targetNode);
            return Optional.ofNullable(details.getDiskStorage());
        } catch (ProxmoxVmNotFoundException e) {
            log.debug("VM {} not found on node {} when retrieving storage", vmid, targetNode);
            return Optional.empty();
        } catch (ProxmoxConnectionException e) {
            log.warn("Could not determine storage for VM {} on node {}: {}", vmid, targetNode, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lists all QEMU VMs on the specified Proxmox node.
     * Falls back to the configured default node if {@code node} is null or blank.
     * Returns an empty list if no VMs are found rather than throwing.
     *
     * @param node the target Proxmox node; falls back to {@code proxmox.api.node} if null or blank
     * @return a list of {@link ProxmoxVmSummary} instances; never null
     * @throws ProxmoxConnectionException if the request fails or times out
     */
    public List<ProxmoxVmSummary> listVms(String node) {
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();
        try {
            String url = buildUrl("/nodes/{node}/qemu");
            @SuppressWarnings("rawtypes") ResponseEntity<ProxmoxApiResponse> response =
                    proxmoxRestTemplate.getForEntity(url, ProxmoxApiResponse.class, targetNode);

            if (response.getBody() == null || response.getBody().getData() == null) {
                log.debug("No VMs found on node {}", targetNode);
                return Collections.emptyList();
            }

            @SuppressWarnings("rawtypes") ProxmoxApiResponse apiResponse = response.getBody();
            List<ProxmoxVmData> vmList = objectMapper.convertValue(
                    apiResponse.getData(),
                    new TypeReference<>() {
                    }
            );

            if (vmList == null || vmList.isEmpty()) {
                log.debug("Empty VM list returned for node {}", targetNode);
                return Collections.emptyList();
            }

            List<ProxmoxVmSummary> summaries = vmList.stream()
                    .filter(Objects::nonNull)
                    .map(this::mapToVmSummary)
                    .collect(Collectors.toList());

            log.info("Listed {} VMs on node {}", summaries.size(), targetNode);
            return summaries;

        } catch (ResourceAccessException e) {
            log.error("Proxmox connection timeout listing VMs on node {}: {}", targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Proxmox connection timeout", e);
        } catch (RestClientException e) {
            log.error("Failed to list VMs on node {}: {}", targetNode, e.getMessage());
            throw new ProxmoxConnectionException("Failed to list VMs: " + e.getMessage(), e);
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