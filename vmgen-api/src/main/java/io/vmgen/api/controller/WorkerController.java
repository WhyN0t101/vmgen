package io.vmgen.api.controller;

import io.vmgen.api.dto.worker.*;
import io.vmgen.api.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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
    public ResponseEntity<?> claimJob(@RequestBody ClaimJobRequest request) {
        log.info("Worker {} requesting job claim", request.getWorkerId());
        
        return workerService.claimNextJob(request.getWorkerId())
            .map(job -> ResponseEntity.ok(job))
            .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Update job progress (prevents timeout detection).
     */
    @PatchMapping("/jobs/{id}/progress")
    public ResponseEntity<?> updateProgress(
            @PathVariable("id") UUID requestId,
            @RequestBody ProgressUpdateRequest request) {
        
        workerService.updateProgress(requestId, request);
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }

    /**
     * Complete job and register artifact.
     * May return 409 if job was already marked failed due to timeout.
     */
    @PostMapping("/jobs/{id}/complete")
    public ResponseEntity<?> completeJob(
            @PathVariable("id") UUID requestId,
            @RequestBody JobCompleteRequest request) {
        
        workerService.completeJob(requestId, request);
        return ResponseEntity.ok(Map.of("registered", true));
    }

    /**
     * Report job failure.
     */
    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<?> failJob(
            @PathVariable("id") UUID requestId,
            @RequestBody JobFailRequest request) {
        
        workerService.failJob(requestId, request);
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }
}
