package io.vmgen.api.service;

import io.vmgen.api.dto.worker.ClaimJobResponse;
import io.vmgen.api.dto.worker.ProgressUpdateRequest;
import io.vmgen.api.dto.worker.JobCompleteRequest;
import io.vmgen.api.dto.worker.JobFailRequest;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.models.Template;
import io.vmgen.api.models.VmRequest;
import io.vmgen.api.models.VmRequestState;
import io.vmgen.api.repository.VmRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final VmRequestRepository vmRequestRepository;

    /**
     * Atomically claims the next queued job for the given worker.
     * Uses FOR UPDATE SKIP LOCKED at the database level to prevent race conditions
     * between concurrent worker instances. Returns empty if no jobs are available.
     */
    @Transactional
    public Optional<ClaimJobResponse> claimNextJob(String workerId) {
        // Step 1: Atomically claim a job (returns number of rows updated)
        int claimed = vmRequestRepository.claimNextJobUpdate(workerId);
        
        if (claimed == 0) {
            log.debug("No jobs available for worker {}", workerId);
            return Optional.empty();
        }
        
        // Step 2: Fetch the claimed job
        Optional<VmRequest> request = vmRequestRepository
                .findFirstByClaimedByWorkerIdAndStateOrderByClaimedAtDesc(workerId, VmRequestState.BUILDING);
        
        if (request.isEmpty()) {
            log.error("Claimed job but couldn't find it - this shouldn't happen");
            return Optional.empty();
        }
        
        log.info("Worker {} claimed job {}", workerId, request.get().getId());
        
        // Build response with full build specification
        return Optional.of(buildClaimResponse(request.get()));
    }

    /**
     * Updates progress for an in-flight job and resets the heartbeat timestamp.
     * Transitions the job to EXPORTING state if the reported stage is "exporting".
     * Throws {@link ConflictException} if the worker does not own the job or the state is invalid.
     */
    @Transactional
    public void updateProgress(UUID requestId, ProgressUpdateRequest update) {
        VmRequest request = vmRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("VmRequest not found: " + requestId));
        
        // Verify worker owns this job
        if (!update.getWorkerId().equals(request.getClaimedByWorkerId())) {
            throw new ConflictException("Worker " + update.getWorkerId() + " does not own this job");
        }
        
        // Verify state allows progress updates
        if (request.getState() != VmRequestState.BUILDING && request.getState() != VmRequestState.EXPORTING) {
            throw new ConflictException("Cannot update progress for job in state: " + request.getState());
        }
        
        // Update progress fields
        request.setCurrentWorkerStage(update.getStage() + ": " + update.getMessage());
        request.setLastProgressAt(Instant.now());
        request.setWorkerLastHeartbeatAt(Instant.now());
        
        // Transition to exporting if indicated
        if ("exporting".equals(update.getStage()) && request.getState() == VmRequestState.BUILDING) {
            request.setState(VmRequestState.EXPORTING);
            request.setExportingStartedAt(Instant.now());
        }
        
        vmRequestRepository.save(request);
        log.debug("Progress updated for job {}: {} ({}%)", requestId, update.getStage(), update.getPercent());
    }


    /**
     * Marks a job as READY and records artifact metadata upon successful completion.
     * Validates the state transition to prevent late completion after a timeout has already
     * marked the job as FAILED.
     * Throws {@link ConflictException} if the worker does not own the job or the transition is invalid.
     */
    @Transactional
    public void completeJob(UUID requestId, JobCompleteRequest completion) {
        VmRequest request = vmRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("VmRequest not found: " + requestId));
        
        // Verify worker owns this job
        if (!completion.getWorkerId().equals(request.getClaimedByWorkerId())) {
            throw new ConflictException("Worker " + completion.getWorkerId() + " does not own this job");
        }
        
        // Validate state transition using state machine
        if (!request.getState().canTransitionTo(VmRequestState.READY)) {
            log.warn("Invalid state transition attempt: {} -> READY for job {}", request.getState(), requestId);
            throw new ConflictException(
                "Cannot complete job in state " + request.getState() + 
                ". Valid transitions: " + request.getState().validTransitions()
            );
        }
        
        // Update with artifact info
        request.setState(VmRequestState.READY);
        request.setCompletedAt(Instant.now());
        request.setStoragePath(completion.getOvaPath());
        request.setOvaSha256(completion.getOvaChecksum());
        request.setOvaSizeBytes(completion.getOvaSizeBytes());
        request.setLastProgressAt(Instant.now());
        
        vmRequestRepository.save(request);
        
        // TODO: Create provenance record
        // TODO: Register flags with flag_value_hash
        
        log.info("Job {} completed successfully by worker {}", requestId, completion.getWorkerId());
    }

    /**
     * Marks a job as FAILED with the provided error details.
     * No-ops silently if the job is already in a terminal state.
     * Throws {@link ConflictException} if the worker does not own the job.
     */
    @Transactional
    public void failJob(UUID requestId, JobFailRequest failure) {
        VmRequest request = vmRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("VmRequest not found: " + requestId));
        
        // Verify worker owns this job (or allow backend to fail any job)
        if (failure.getWorkerId() != null && 
            !failure.getWorkerId().equals(request.getClaimedByWorkerId())) {
            throw new ConflictException("Worker " + failure.getWorkerId() + " does not own this job");
        }
        
        // Validate state transition (most states can fail, but check anyway)
        if (!request.getState().canTransitionTo(VmRequestState.FAILED)) {
            log.warn("Job {} in terminal state {}, ignoring fail request", requestId, request.getState());
            return; // Already in terminal state
        }
        
        request.setState(VmRequestState.FAILED);
        request.setFailedAt(Instant.now());
        request.setFailureReason(failure.getErrorType() + ": " + failure.getMessage());
        request.setLastProgressAt(Instant.now());
        
        vmRequestRepository.save(request);
        log.error("Job {} failed: {} - {}", requestId, failure.getErrorType(), failure.getMessage());
    }

    /**
     * Builds the full job specification response returned to the worker upon claiming a job.
     */
    private ClaimJobResponse buildClaimResponse(VmRequest request) {

        Template template = request.getTemplate();
        // TODO: Fetch scripts from vm_request_scripts table
        
        return ClaimJobResponse.builder()
            .requestId(request.getId())
            .templateIdentifier(template.getIdentifier())
            .templateVersion(template.getVersion())
            .templateSha256(template.getSha256Hash())
            .proxmoxVmid(template.getProxmoxVmid())
            .proxmoxNode(template.getProxmoxNode())
            .buildSeed(request.getBuildSeed())
            .difficultyLevel(request.getDifficultyOverride())
            .telemetryEnabled(request.getTelemetryEnabled())
            // .scripts(scriptInfoList)  // TODO: populate from vm_request_scripts
            .build();
    }

    /**
     * Marks all BUILDING and EXPORTING jobs as FAILED if no progress update has been
     * received within the given timeout duration.
     * Intended to be called periodically by a scheduled task.
     */
    @Transactional
    public void markStaleJobsAsFailed(Duration timeout){
    Instant threshold = Instant.now().minus(timeout);
        List<VmRequest> staleJobs = vmRequestRepository.findStaleJobs(
                List.of(VmRequestState.BUILDING, VmRequestState.EXPORTING), threshold);

        staleJobs.forEach(job -> {
            job.setState(VmRequestState.FAILED);
            job.setFailedAt(Instant.now());
            job.setFailureReason("TIMEOUT: No progress update for " + timeout.toMinutes() + " minutes");
        });
        vmRequestRepository.saveAll(staleJobs);
        
        if (!staleJobs.isEmpty()) {
            log.info("Marked {} stale jobs as failed", staleJobs.size());
        }
    }
}
