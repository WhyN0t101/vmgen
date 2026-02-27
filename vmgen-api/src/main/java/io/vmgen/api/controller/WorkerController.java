package io.vmgen.api.controller;

import io.vmgen.api.dto.worker.ClaimJobRequest;
import io.vmgen.api.dto.worker.ClaimJobResponse;
import io.vmgen.api.dto.worker.JobCompleteRequest;
import io.vmgen.api.dto.worker.JobFailRequest;
import io.vmgen.api.dto.worker.ProgressUpdateRequest;
import io.vmgen.api.dto.worker.WorkerAckResponse;
import io.vmgen.api.dto.worker.WorkerRegisteredResponse;
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


/**
 * Worker-facing API endpoints.
 * Authentication is handled upstream by WorkerApiKeyFilter via API key validation.
 * Spring Security role-based access is intentionally not applied here.
 */
@RestController
@RequestMapping("/api/worker")
@RequiredArgsConstructor
@Slf4j
public class WorkerController {

    private final WorkerService workerService;

    /**
     * Claim the next available job (atomic dequeue).
     * Returns 204 No Content if no jobs available.
     */
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
    public ResponseEntity<WorkerRegisteredResponse> completeJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobCompleteRequest request) {
        
        workerService.completeJob(requestId, request);
        return ResponseEntity.ok(new WorkerRegisteredResponse(true));
    }

    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<WorkerAckResponse> failJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobFailRequest request) {
        
        workerService.failJob(requestId, request);
        return ResponseEntity.ok(new WorkerAckResponse(true));
    }
}
