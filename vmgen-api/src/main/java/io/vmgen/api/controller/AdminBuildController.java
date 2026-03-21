package io.vmgen.api.controller;

import io.vmgen.api.dto.build.AdminBuildCredentialsResponse;
import io.vmgen.api.dto.build.AdminBuildSummaryDto;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.service.VmRequestService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/builds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBuildController {

    private final VmRequestService vmRequestService;

    @GetMapping
    public ResponseEntity<PagedResponse<AdminBuildSummaryDto>> getAllBuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(vmRequestService.getAllBuilds(page, size));
    }

    @GetMapping("/{id}/credentials")
    public ResponseEntity<AdminBuildCredentialsResponse> getBuildCredentials(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(vmRequestService.getAdminBuildCredentials(id));
    }
}
