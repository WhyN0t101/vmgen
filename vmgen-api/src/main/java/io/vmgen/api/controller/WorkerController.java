package io.vmgen.api.controller;

import io.vmgen.api.dto.worker.AppendBuildLogRequest;
import io.vmgen.api.dto.worker.ClaimJobRequest;
import io.vmgen.api.dto.worker.ClaimJobResponse;
import io.vmgen.api.dto.worker.DeriveFlagsRequest;
import io.vmgen.api.dto.worker.DeriveFlagsResponse;
import io.vmgen.api.dto.worker.HeartbeatRequest;
import io.vmgen.api.dto.worker.JobCompleteRequest;
import io.vmgen.api.dto.worker.JobFailRequest;
import io.vmgen.api.dto.worker.ProgressUpdateRequest;
import io.vmgen.api.dto.worker.WorkerAckResponse;
import io.vmgen.api.service.WorkerService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
@Slf4j
public class WorkerController {

    private final WorkerService workerService;


    @PostMapping("/heartbeat")
    public ResponseEntity<WorkerAckResponse> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        workerService.recordHeartbeat(
                request.getWorkerId(),
                request.getActiveJobIds(),
                request.getCpuPercent(),
                request.getMemoryPercent());
        return ResponseEntity.ok(new WorkerAckResponse(true));
    }

    @PostMapping("/jobs/claim")
    public ResponseEntity<ClaimJobResponse> claimJob(@Valid @RequestBody ClaimJobRequest request) {
        log.info("Worker {} requesting job claim", request.getWorkerId());
        
        return workerService.claimNextJob(request.getWorkerId())
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }


    @PatchMapping("/jobs/{id}/progress")
    public ResponseEntity<WorkerAckResponse> updateProgress(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody ProgressUpdateRequest request) {
        
        workerService.updateProgress(requestId, request);
        return ResponseEntity.ok(new WorkerAckResponse(true));
    }


    @PostMapping("/jobs/{id}/complete")
    public ResponseEntity<WorkerAckResponse> completeJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobCompleteRequest request) {
        
        workerService.completeJob(requestId, request);
        return ResponseEntity.ok(new WorkerAckResponse(true));    }

    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<WorkerAckResponse> failJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobFailRequest request) {

        workerService.failJob(requestId, request);
        return ResponseEntity.ok(new WorkerAckResponse(true));
    }


    @PostMapping("/jobs/{id}/logs")
    public ResponseEntity<WorkerAckResponse> appendBuildLog(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody AppendBuildLogRequest request) {

        workerService.appendBuildLog(requestId, request);
        return ResponseEntity.ok(new WorkerAckResponse(true));
    }

    @PostMapping("/jobs/{id}/derive-flags")
    public ResponseEntity<DeriveFlagsResponse> deriveFlags(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody DeriveFlagsRequest request) {

        DeriveFlagsResponse response = workerService.deriveBuildSecrets(requestId, request);
        return ResponseEntity.ok(response);
    }
}
