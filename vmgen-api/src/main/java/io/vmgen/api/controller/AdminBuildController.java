package io.vmgen.api.controller;

import io.vmgen.api.dto.build.AdminBuildCredentialsResponse;
import io.vmgen.api.dto.build.AdminBuildSummaryDto;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.telemetry.TelemetryUploadResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.service.TelemetryService;
import io.vmgen.api.service.UserService;
import io.vmgen.api.service.VmRequestService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static io.vmgen.api.utils.HttpUtils.auditContext;

@RestController
@RequestMapping("/api/admin/builds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBuildController {

    private final VmRequestService vmRequestService;
    private final TelemetryService telemetryService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<PagedResponse<AdminBuildSummaryDto>> getAllBuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(vmRequestService.getAllBuilds(page, size));
    }

    @GetMapping("/{id}/credentials")
    public ResponseEntity<AdminBuildCredentialsResponse> getBuildCredentials(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User admin = userService.requireAdminUser(authentication);
        return ResponseEntity.ok(vmRequestService.getAdminBuildCredentials(id, admin, auditContext(http, Map.of())));
    }

    @GetMapping("/{id}/telemetry")
    public ResponseEntity<TelemetryUploadResponse> getBuildTelemetry(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        User admin = userService.requireAdminUser(authentication);
        return ResponseEntity.ok(telemetryService.getAdminTelemetry(id, admin, auditContext(http, Map.of())));
    }
}
