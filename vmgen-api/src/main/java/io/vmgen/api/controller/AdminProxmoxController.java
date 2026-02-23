package io.vmgen.api.controller;

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

    public AdminProxmoxController(ProxmoxService proxmoxService) {
        this.proxmoxService = proxmoxService;
    }

    @GetMapping("/vms")
    public ResponseEntity<List<ProxmoxVmSummary>> listVms(@RequestParam(required = false) String node) {
        return ResponseEntity.ok(proxmoxService.listVms(node));
    }

    @GetMapping("/vm-storage")
    public ResponseEntity<java.util.Map<String, String>> getVmStorage(
            @RequestParam Integer vmid,
            @RequestParam(required = false) String node
    ) {
        return proxmoxService.getVmStorage(vmid, node)
                .map(storage -> ResponseEntity.ok(java.util.Map.of("storage", storage)))
                .orElse(ResponseEntity.notFound().build());
    }
}

