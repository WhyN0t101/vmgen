package io.vmgen.api.controller;

import io.vmgen.api.config.ProxmoxApiConfig;
import io.vmgen.api.dto.proxmox.ProxmoxVmSummary;
import io.vmgen.api.service.ProxmoxService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/proxmox")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProxmoxController {

    private final ProxmoxService proxmoxService;
    private final ProxmoxApiConfig proxmoxConfig;

    public AdminProxmoxController(ProxmoxService proxmoxService, ProxmoxApiConfig proxmoxConfig) {
        this.proxmoxService = proxmoxService;
        this.proxmoxConfig = proxmoxConfig;
    }

    @GetMapping("/vms")
    public ResponseEntity<List<ProxmoxVmSummary>> listVms(@RequestParam(required = false) String node) {
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();
        return ResponseEntity.ok(proxmoxService.listVms(targetNode));
    }
    
    @GetMapping("/vm-storage")
    public ResponseEntity<java.util.Map<String, String>> getVmStorage(
            @RequestParam Integer vmid,
            @RequestParam(required = false) String node
    ) {
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();
        String storage = proxmoxService.getVmStorage(vmid, targetNode);
        return ResponseEntity.ok(java.util.Map.of("storage", storage != null ? storage : ""));
    }
}

