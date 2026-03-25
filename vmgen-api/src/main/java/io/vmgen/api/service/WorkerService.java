package io.vmgen.api.service;

import io.vmgen.api.dto.worker.AppendBuildLogRequest;
import io.vmgen.api.dto.worker.ClaimJobResponse;
import io.vmgen.api.dto.worker.ProgressUpdateRequest;
import io.vmgen.api.dto.worker.JobCompleteRequest;
import io.vmgen.api.dto.worker.JobFailRequest;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.models.build.BuildLog;
import io.vmgen.api.models.flag.Flag;
import io.vmgen.api.models.provenance.ProvenanceRecord;
import io.vmgen.api.models.provenance.ProvenanceScript;
import io.vmgen.api.models.template.Template;
import io.vmgen.api.models.vm.VmRequest;
import io.vmgen.api.models.vm.VmRequestScript;
import io.vmgen.api.models.vm.VmRequestScriptParameter;
import io.vmgen.api.models.vm.VmRequestState;
import io.vmgen.api.repository.BuildLogRepository;
import io.vmgen.api.repository.flag.FlagRepository;
import io.vmgen.api.repository.provenance.ProvenanceRecordRepository;
import io.vmgen.api.repository.script.ScriptRepository;
import io.vmgen.api.repository.template.TemplateRepository;
import io.vmgen.api.repository.vm.VmRequestRepository;
import io.vmgen.api.repository.vm.VmRequestScriptParameterRepository;
import io.vmgen.api.repository.vm.VmRequestScriptRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerService {

    private final VmRequestRepository vmRequestRepository;
    private final VmRequestScriptRepository vmRequestScriptRepository;
    private final VmRequestScriptParameterRepository vmRequestScriptParameterRepository;
    private final ProvenanceRecordRepository provenanceRecordRepository;
    private final FlagRepository flagRepository;
    private final ScriptRepository scriptRepository;
    private final TemplateRepository templateRepository;
    private final BuildLogRepository buildLogRepository;
    private final AuditService auditService;

    @Value("${vmgen.platform.secret}")
    private String platformSecret;

    // In-memory worker liveness tracking — keyed by workerId, value is last heartbeat timestamp.
    // Not persisted: resets on backend restart, which is acceptable for a lab deployment.
    private final Map<String, Instant> workerHeartbeats = new ConcurrentHashMap<>();

    /**
     * Records a liveness heartbeat from a worker.
     * Called every 60 seconds by each worker regardless of build activity.
     */
    public void recordHeartbeat(String workerId, List<String> activeJobIds) {
        workerHeartbeats.put(workerId, Instant.now());
        log.debug("Heartbeat from worker {} (active jobs: {})", workerId, activeJobIds);
    }

    /**
     * Returns worker IDs that have not sent a heartbeat within the given threshold.
     * Used by the scheduled task to log warnings about potentially dead workers.
     */
    public List<String> getStaleWorkers(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        return workerHeartbeats.entrySet().stream()
                .filter(e -> e.getValue().isBefore(cutoff))
                .map(Map.Entry::getKey)
                .toList();
    }

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
        if (!Objects.equals(update.getWorkerId(), request.getClaimedByWorkerId())) {
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
        if ("exporting".equalsIgnoreCase(update.getStage()) && request.getState() == VmRequestState.BUILDING) {
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
        if (!Objects.equals(completion.getWorkerId(), request.getClaimedByWorkerId())) {            throw new ConflictException("Worker " + completion.getWorkerId() + " does not own this job");
        }
        
        // Validate state transition using state machine
        if (!request.getState().canTransitionTo(VmRequestState.READY)) {
            log.warn("Invalid state transition attempt: {} -> READY for job {}", request.getState(), requestId);
            throw new ConflictException(
                "Cannot complete job in state " + request.getState() + 
                ". Valid transitions: " + request.getState().validTransitions()
            );
        }
        
        // Update with artefact info
        request.setState(VmRequestState.READY);
        request.setCompletedAt(Instant.now());
        request.setStoragePath(completion.getOvaPath());
        request.setOvaFilename(Paths.get(completion.getOvaPath()).getFileName().toString());
        request.setOvaSha256(completion.getOvaChecksum());
        request.setOvaSizeBytes(completion.getOvaSizeBytes());
        request.setLastProgressAt(Instant.now());
        
        vmRequestRepository.save(request);

        // Create provenance record
        if (completion.getProvenance() != null) {
            persistProvenance(completion, request);
        }

        // Register flag hashes — re-derive each hash independently to verify worker output
        if (completion.getFlags() != null && !completion.getFlags().isEmpty()) {
            verifyAndPersistFlags(requestId, completion, request);
        }

        auditService.logWorkerJobCompleted(completion.getWorkerId(), requestId);
        log.info("Job {} completed successfully by worker {}", requestId, completion.getWorkerId());
    }

    private void verifyAndPersistFlags(UUID requestId, JobCompleteRequest completion, VmRequest request) {
        String flagDerivationTimestamp = (completion.getProvenance() != null)
                ? completion.getProvenance().getFlagDerivationTimestamp()
                : null;
        Instant flagTs = (flagDerivationTimestamp != null)
                ? Instant.parse(flagDerivationTimestamp)
                : Instant.now();

        for (JobCompleteRequest.FlagInfo fi : completion.getFlags()) {
            String tsUsed = flagDerivationTimestamp != null ? flagDerivationTimestamp : flagTs.toString();
            String expectedHash = deriveFlagHash(
                    request.getBuildSeed(),
                    requestId.toString(),
                    fi.getScriptId(),
                    tsUsed
            );

            if (!MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    fi.getFlagValueHash().getBytes(StandardCharsets.UTF_8))) {
                log.warn("Flag hash mismatch for job {} script {} — worker output rejected",
                        requestId, fi.getScriptId());
                throw new ConflictException(
                        "Flag hash verification failed for script " + fi.getScriptId());
            }
        }

        List<Flag> flags = new ArrayList<>();
        for (JobCompleteRequest.FlagInfo fi : completion.getFlags()) {
            UUID scriptId = UUID.fromString(fi.getScriptId());
            scriptRepository.findById(scriptId).ifPresent(script -> {
                Flag flag = new Flag();
                flag.setVmRequest(request);
                flag.setScript(script);
                flag.setFlagValueHash(fi.getFlagValueHash());
                flag.setFlagDerivationTimestamp(flagTs);
                flags.add(flag);
            });
        }
        flagRepository.saveAll(flags);
    }

    private void persistProvenance(JobCompleteRequest completion, VmRequest request) {
        JobCompleteRequest.Provenance p = completion.getProvenance();
        ProvenanceRecord provenance = new ProvenanceRecord();
        provenance.setVmRequest(request);
        provenance.setTemplateIdentifier(p.getTemplateIdentifier());
        provenance.setTemplateVersion(p.getTemplateVersion());
        provenance.setTemplateSha256(p.getTemplateSha256() != null ? p.getTemplateSha256() : "");
        provenance.setBuildSeed(request.getBuildSeed());
        provenance.setOvaSha256(completion.getOvaChecksum());
        provenance.setDifficultyLevel(request.getDifficultyOverride());
        provenance.setTelemetryEnabled(Boolean.TRUE.equals(request.getTelemetryEnabled()));
        provenance.setWorkerId(p.getWorkerId());
        provenance.setVmLoginPassword(completion.getVmLoginPassword());
        if (p.getBuildStartedAt() != null) {
            try { provenance.setBuildStartedAt(Instant.parse(p.getBuildStartedAt())); }
            catch (DateTimeParseException e) {
                log.warn("Unparseable provenance timestamp, field skipped: {}", e.getMessage());
            }
        }
        if (p.getBuildFinishedAt() != null) {
            try { provenance.setBuildFinishedAt(Instant.parse(p.getBuildFinishedAt())); }
            catch (DateTimeParseException e) {
                log.warn("Unparseable provenance timestamp, field skipped: {}", e.getMessage());
            }
        }
        if (p.getFlagDerivationTimestamp() != null) {
            provenance.setFlagDerivationTimestamp(
                Instant.parse(p.getFlagDerivationTimestamp()));
        } else {
            provenance.setFlagDerivationTimestamp(Instant.now());
        }

        // Persist script execution records
        if (p.getScripts() != null) {
            List<ProvenanceScript> scriptRecords = new ArrayList<>();
            for (int i = 0; i < p.getScripts().size(); i++) {
                JobCompleteRequest.ScriptRecord sr = p.getScripts().get(i);
                ProvenanceScript ps = new ProvenanceScript();
                ps.setProvenanceRecord(provenance);
                ps.setScriptIdentifier(sr.getIdentifier() != null ? sr.getIdentifier() : "");
                ps.setScriptVersion(sr.getVersion() != null ? sr.getVersion() : "");
                ps.setScriptSha256(sr.getSha256() != null ? sr.getSha256() : "");
                ps.setExecutionOrder(sr.getExecutionOrder() != null ? sr.getExecutionOrder() : i);
                if (sr.getScriptId() != null) {
                    try { ps.setScriptId(UUID.fromString(sr.getScriptId())); }
                    catch (IllegalArgumentException ignored) {}
                }
                ps.setExitCode(sr.getExitCode());
                ps.setDurationSeconds(sr.getDurationSeconds());
                scriptRecords.add(ps);
            }
            provenance.setScripts(scriptRecords);
        }

        provenanceRecordRepository.save(provenance);

        // Update the template's sha256Hash if it was pending and the worker computed a real one
        String workerTemplateSha256 = p.getTemplateSha256();
        if (workerTemplateSha256 != null && !workerTemplateSha256.isBlank()
                && !workerTemplateSha256.equals("PENDING")) {
            templateRepository.findById(request.getTemplate().getId()).ifPresent(template -> {
                if ("PENDING".equals(template.getSha256Hash())) {
                    template.setSha256Hash(workerTemplateSha256);
                    templateRepository.save(template);
                    log.info("Updated template {} sha256Hash from PENDING to computed value",
                            template.getId());
                }
            });
        }
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
        auditService.logWorkerJobFailed(
                failure.getWorkerId() != null ? failure.getWorkerId() : "unknown",
                requestId,
                failure.getErrorType() != null ? failure.getErrorType() : "unknown");
        log.error("Job {} failed: {} - {}", requestId, failure.getErrorType(), failure.getMessage());
    }

    /**
     * Builds the full job specification response returned to the worker upon claiming a job.
     */
    private ClaimJobResponse buildClaimResponse(VmRequest request) {
        Template template = request.getTemplate();

        // Load all parameter values for this request keyed by script ID
        List<VmRequestScriptParameter> allParams =
                vmRequestScriptParameterRepository.findByVmRequest_Id(request.getId());
        Map<UUID, Map<String, String>> paramsByScriptId = allParams.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getScript().getId(),
                        Collectors.toMap(
                                VmRequestScriptParameter::getParameterName,
                                VmRequestScriptParameter::getParameterValue)));

        // Load scripts in execution order with their Script entities eager-fetched
        List<VmRequestScript> orderedScripts =
                vmRequestScriptRepository.findWithScriptByVmRequestId(request.getId());

        List<ClaimJobResponse.ScriptInfo> scriptInfos = orderedScripts.stream()
                .map(vrs -> ClaimJobResponse.ScriptInfo.builder()
                        .scriptId(vrs.getScript().getId())
                        .identifier(vrs.getScript().getIdentifier())
                        .version(vrs.getScript().getVersion())
                        .sha256(vrs.getScript().getSha256Hash())
                        .flagLocation(vrs.getScript().getFlagLocation())
                        .executionOrder(vrs.getExecutionOrder())
                        .scriptContent(vrs.getScript().getScriptContent())
                        .parameterValues(paramsByScriptId.getOrDefault(vrs.getScript().getId(), Map.of()))
                        .build())
                .toList();

        return ClaimJobResponse.builder()
                .requestId(request.getId())
                .templateIdentifier(template.getIdentifier())
                .templateVersion(template.getVersion())
                .templateSha256(template.getSha256Hash())
                .proxmoxVmid(template.getProxmoxVmid())
                .proxmoxNode(template.getProxmoxNode())
                .sshAuthMethod(template.getSshAuthMethod())
                .expectedSshUser(template.getExpectedSshUser())
                .expectedSshPort(template.getExpectedSshPort())
                .sshPassword(template.getSshPassword())
                .sshPrivateKey(template.getSshPrivateKey())
                .diskStorage(template.getDiskStorage())
                .buildSeed(request.getBuildSeed())
                .difficultyLevel(request.getDifficultyOverride())
                .telemetryEnabled(request.getTelemetryEnabled())
                .scripts(scriptInfos)
                .build();
    }

    /**
     * Appends a single log entry to the build log for the given job.
     * Accepts log writes for jobs in any state (including FAILED) so that error entries
     * emitted just before a fail_job call are never rejected.
     * Throws {@link ConflictException} if the calling worker does not own the job.
     */
    @Transactional
    public void appendBuildLog(UUID requestId, AppendBuildLogRequest request) {
        VmRequest vmRequest = vmRequestRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("VmRequest not found: " + requestId));

        if (!request.getWorkerId().equals(vmRequest.getClaimedByWorkerId())) {
            throw new ConflictException(
                "Worker " + request.getWorkerId() + " does not own job " + requestId);
        }

        BuildLog entry = new BuildLog();
        entry.setVmRequest(vmRequest);
        entry.setLogLevel(request.getLogLevel());
        entry.setStage(request.getStage());
        entry.setMessage(request.getMessage());

        if (request.getLoggedAt() != null) {
            try {
                entry.setLoggedAt(Instant.parse(request.getLoggedAt()));
            } catch (DateTimeParseException e) {
                // Unparseable worker timestamp — @PrePersist will set loggedAt = Instant.now()
                log.debug("Unparseable loggedAt '{}' for job {} — using server time",
                    request.getLoggedAt(), requestId);
            }
        }

        buildLogRepository.save(entry);
    }

    /**
     * Marks all BUILDING and EXPORTING jobs as FAILED if no progress update has been
     * received within the given timeout duration.
     * Intended to be called periodically by a scheduled task.
     */
    @Transactional
    public void markStaleJobsAsFailed(Duration timeout) {
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

    /**
     * Marks all BUILDING and EXPORTING jobs as FAILED if their wall-clock age exceeds
     * the given duration (absolute timeout — independent of heartbeat activity).
     * Intended to be called periodically by a scheduled task.
     */
    @Transactional
    public void markLongRunningJobsAsFailed(Duration maxDuration) {
        Instant threshold = Instant.now().minus(maxDuration);
        List<VmRequest> longRunningJobs = vmRequestRepository.findLongRunningJobs(
                List.of(VmRequestState.BUILDING, VmRequestState.EXPORTING), threshold);

        longRunningJobs.forEach(job -> {
            job.setState(VmRequestState.FAILED);
            job.setFailedAt(Instant.now());
            job.setFailureReason("TIMEOUT: Build exceeded maximum duration of " + maxDuration.toHours() + " hours");
        });
        vmRequestRepository.saveAll(longRunningJobs);

        if (!longRunningJobs.isEmpty()) {
            log.warn("Marked {} long-running jobs as failed (exceeded {}h)", longRunningJobs.size(), maxDuration.toHours());
        }
    }

    /**
     * Re-derives the flag hash using the same algorithm as the worker (build/flags.py):
     *   HMAC-SHA256(key=platformSecret, msg=seedHex||requestId||scriptId||timestamp)
     *   → first 17 bytes, zero last 6 bits of byte[16] (& 0xC0)
     *   → Base32 (no padding) → "FLAG{<26 chars>}"
     *   → SHA-256 hex of that string
     */
    private String deriveFlagHash(String seedHex, String requestId, String scriptId, String timestamp) {
        try {
            byte[] key = platformSecret.getBytes(StandardCharsets.UTF_8);
            byte[] message = (seedHex + requestId + scriptId + timestamp).getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(message);

            // Take first 17 bytes, zero last 6 bits of byte[16] — yields 130 bits
            byte[] raw = new byte[17];
            System.arraycopy(digest, 0, raw, 0, 17);
            raw[16] = (byte) (raw[16] & 0xC0);

            // Base32-encode (RFC 4648) and strip padding
            String encoded = new Base32().encodeToString(raw).replace("=", "");
            String flagString = "FLAG{" + encoded + "}";

            // SHA-256 of the flag string — this is what the worker sends
            byte[] hashBytes = MessageDigest.getInstance("SHA-256")
                    .digest(flagString.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Flag derivation failed — crypto provider error", e);
        }
    }
}
