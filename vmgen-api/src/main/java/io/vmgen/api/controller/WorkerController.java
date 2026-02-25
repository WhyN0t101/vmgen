package io.vmgen.api.controller;

import io.vmgen.api.dto.worker.*;
import jakarta.validation.Valid;
import io.vmgen.api.service.WorkerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;


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
    public ResponseEntity<Map<String, Boolean>> updateProgress(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody ProgressUpdateRequest request) {
        
        workerService.updateProgress(requestId, request);
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }


    @PostMapping("/jobs/{id}/complete")
    public ResponseEntity<Map<String, Boolean>> completeJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobCompleteRequest request) {
        
        workerService.completeJob(requestId, request);
        return ResponseEntity.ok(Map.of("registered", true));
    }

    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<Map<String, Boolean>> failJob(
            @PathVariable("id") UUID requestId,
            @Valid @RequestBody JobFailRequest request) {
        
        workerService.failJob(requestId, request);
        return ResponseEntity.ok(Map.of("acknowledged", true));
    }
}
