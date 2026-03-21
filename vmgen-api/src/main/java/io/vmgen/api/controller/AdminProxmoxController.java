package io.vmgen.api.controller;

import io.vmgen.api.dto.proxmox.ProxmoxVmSummary;
import io.vmgen.api.service.ProxmoxService;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/proxmox")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminProxmoxController {

    private final ProxmoxService proxmoxService;


    @GetMapping("/vms")
    public ResponseEntity<List<ProxmoxVmSummary>> listVms(@RequestParam(required = false) String node) {
        return ResponseEntity.ok(proxmoxService.listVms(node));
    }

    @GetMapping("/vm-storage")
    public ResponseEntity<Map<String, String>> getVmStorage(
            @RequestParam @Min(1) Integer vmid,
            @RequestParam(required = false) String node
    ) {
        return proxmoxService.getVmStorage(vmid, node)
                .map(storage -> ResponseEntity.ok(Map.of("storage", storage)))
                .orElse(ResponseEntity.notFound().build());
    }
}

