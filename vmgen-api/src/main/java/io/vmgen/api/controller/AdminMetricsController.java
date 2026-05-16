package io.vmgen.api.controller;

import io.vmgen.api.dto.admin.AdminMetricsResponse;
import io.vmgen.api.service.AdminMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AdminMetricsService adminMetricsService;

    @GetMapping("/metrics")
    public ResponseEntity<AdminMetricsResponse> getMetrics() {
        AdminMetricsResponse response = AdminMetricsResponse.builder()
                .queueDepth(adminMetricsService.getQueueDepth())
                .avgWaitMinutes(adminMetricsService.getAvgWaitMinutes())
                .completionRate24h(adminMetricsService.getCompletionRate24h())
                .workers(adminMetricsService.getWorkerStatus())
                .dbStatus(adminMetricsService.getDbStatus())
                .storageTotalBytes(adminMetricsService.getStorageTotalBytes())
                .storageUsableBytes(adminMetricsService.getStorageUsableBytes())
                .build();
        return ResponseEntity.ok(response);
    }
}
