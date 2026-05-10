package io.vmgen.api.service;

import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.build.AdminBuildCredentialsResponse;
import io.vmgen.api.dto.build.AdminBuildSummaryDto;
import io.vmgen.api.dto.build.BuildDownloadInfo;
import io.vmgen.api.dto.build.BuildResponse;
import io.vmgen.api.dto.build.BuildScriptDto;
import io.vmgen.api.dto.build.CreateBuildRequest;
import io.vmgen.api.dto.build.ProvenanceResponse;
import io.vmgen.api.models.provenance.ProvenanceRecord;
import io.vmgen.api.models.vm.VmRequestState;
import io.vmgen.api.repository.provenance.ProvenanceRecordRepository;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.dto.script.ScriptSelectionResult;
import io.vmgen.api.exceptions.*;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.template.ScriptCompatibility;
import io.vmgen.api.models.template.Template;
import io.vmgen.api.models.template.TemplateStatus;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.vm.VmRequest;
import io.vmgen.api.models.vm.VmRequestScript;
import io.vmgen.api.models.vm.VmRequestScriptParameter;
import io.vmgen.api.repository.vm.VmRequestRepository;
import io.vmgen.api.repository.vm.VmRequestScriptParameterRepository;
import io.vmgen.api.repository.vm.VmRequestScriptRepository;
import io.vmgen.api.repository.script.ScriptCompatibilityRepository;
import io.vmgen.api.repository.script.ScriptRepository;
import io.vmgen.api.repository.template.TemplateRepository;
import io.vmgen.api.service.script.ScriptCompatibilityService;
import io.vmgen.api.service.script.ScriptParameterService;
import io.vmgen.api.service.script.ScriptParameterService.ParameterProcessingResult;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static io.vmgen.api.models.script.ValueSource.USER_INPUT_OR_RANDOM;

@Service
@RequiredArgsConstructor
@Slf4j
public class VmRequestService {

    private final VmRequestRepository vmRequestRepository;
    private final VmRequestScriptRepository vmRequestScriptRepository;
    private final VmRequestScriptParameterRepository scriptParameterRepository;
    private final TemplateRepository templateRepository;
    private final ScriptRepository scriptRepository;
    private final ScriptCompatibilityRepository compatibilityRepository;
    private final ScriptCompatibilityService scriptCompatibilityService;
    private final ScriptParameterService scriptParameterService;
    private final ProvenanceRecordRepository provenanceRecordRepository;
    private final AuditService auditService;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Validates the request, creates a QUEUED VmRequest, and persists all
     * script entries and parameter values. Returns the full BuildResponse.
     */
    @Transactional
    public BuildResponse createBuild(User user, CreateBuildRequest request, AuditContext ctx) {
        // 0. Reject duplicate script IDs up front — the count check below would otherwise
        //    report them as "not found or inactive", which is a misleading error.
        List<UUID> requestedIds = request.getVulnerabilityIds();
        Set<UUID> uniqueIds = new HashSet<>(requestedIds);
        if (uniqueIds.size() != requestedIds.size()) {
            throw new ValidationException("Validation failed",
                    List.of("Duplicate script IDs in request"));
        }

        // 1. Validate template exists and is ACTIVE.
        Template template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + request.getTemplateId()));
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new ValidationException("Validation failed",
                    List.of("Template is not active (status: " + template.getStatus() + ")"));
        }

        // 2. Validate all scripts exist and are active.
        List<Script> userScripts = scriptRepository.findActiveByIdsWithCategory(request.getVulnerabilityIds());
        if (userScripts.size() != request.getVulnerabilityIds().size()) {
            Set<UUID> found = userScripts.stream().map(Script::getId).collect(Collectors.toSet());
            List<UUID> missing = request.getVulnerabilityIds().stream()
                    .filter(id -> !found.contains(id)).toList();
            throw new ValidationException("Validation failed",
                    List.of("Scripts not found or inactive: " + missing));
        }

        // 3. Validate compatibility: if the template has explicit compatibility entries,
        //    every requested script must be in that list.
        List<ScriptCompatibility> compatEntries = compatibilityRepository.findAllByTemplateId(request.getTemplateId());
        if (!compatEntries.isEmpty()) {
            Set<UUID> compatibleIds = compatEntries.stream()
                    .map(ScriptCompatibility::getScriptId)
                    .collect(Collectors.toSet());
            List<UUID> incompatible = request.getVulnerabilityIds().stream()
                    .filter(id -> !compatibleIds.contains(id)).toList();
            if (!incompatible.isEmpty()) {
                throw new ValidationException("Validation failed",
                        List.of("Scripts not compatible with this template: " + incompatible));
            }
        }

        // 4. Resolve script selection: reject conflicts, expand dependencies, get execution order.
        ScriptSelectionResult resolved;
        try {
            resolved = scriptCompatibilityService.resolveScriptSelection(request.getVulnerabilityIds());
        } catch (ScriptConflictException ex) {
            List<String> details = ex.getConflicts().isEmpty()
                    ? List.of(ex.getMessage())
                    : ex.getConflicts();
            throw new ValidationException("Script compatibility validation failed", details);
        } catch (CircularDependencyException ex) {
            throw new ValidationException("Script compatibility validation failed", List.of(ex.getMessage()));
        }

        List<UUID> orderedScriptIds = resolved.getOrderedScriptIds();
        Set<UUID> autoAddedIds = new HashSet<>(resolved.getAutoAddedScriptIds() != null ? resolved.getAutoAddedScriptIds() : List.of());

        // 4b. Validate auto-added dependencies against the template's compatibility list.
        //     Step 3 only checked user-requested scripts; dependencies expanded by resolveScriptSelection
        //     must also be explicitly compatible when the template has a restricted compat list.
        if (!compatEntries.isEmpty() && !autoAddedIds.isEmpty()) {
            Set<UUID> compatibleIds = compatEntries.stream()
                    .map(ScriptCompatibility::getScriptId)
                    .collect(Collectors.toSet());
            List<UUID> incompatibleDeps = autoAddedIds.stream()
                    .filter(id -> !compatibleIds.contains(id)).toList();
            if (!incompatibleDeps.isEmpty()) {
                throw new ValidationException("Validation failed",
                        List.of("Required dependency scripts are not compatible with this template: " + incompatibleDeps));
            }
        }

        // 5. Ensure all resolved scripts (including auto-added dependencies) exist and are active.
        List<Script> scripts = scriptRepository.findActiveByIdsWithCategory(orderedScriptIds);
        if (scripts.size() != orderedScriptIds.size()) {
            Set<UUID> found = scripts.stream().map(Script::getId).collect(Collectors.toSet());
            List<UUID> missing = orderedScriptIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ValidationException("Validation failed",
                    List.of("One or more required dependency scripts are not found or inactive: " + missing));
        }

        // 6. Build the VmRequest, save it first to obtain the generated ID.
        VmRequest vmRequest = new VmRequest();
        vmRequest.setUser(user);
        vmRequest.setTemplate(template);
        vmRequest.setBuildSeed(generateBuildSeed());
        vmRequest.setDifficultyOverride(request.getDifficulty());
        vmRequest.setTelemetryEnabled(request.isTelemetryEnabled());

        VmRequest saved = vmRequestRepository.save(vmRequest);

        // Now create VmRequestScript entries (use resolved order and mark auto-added).
        Map<UUID, Script> scriptById = scripts.stream()
                .collect(Collectors.toMap(Script::getId, s -> s));

        List<VmRequestScript> requestScripts = new ArrayList<>();
        for (int i = 0; i < orderedScriptIds.size(); i++) {
            UUID scriptId = orderedScriptIds.get(i);
            VmRequestScript vrs = new VmRequestScript();
            vrs.setVmRequestId(saved.getId());
            vrs.setScriptId(scriptId);
            vrs.setExecutionOrder(i + 1);
            vrs.setAutoAdded(autoAddedIds.contains(scriptId));
            requestScripts.add(vrs);
        }
        vmRequestScriptRepository.saveAll(requestScripts);
        // Note: do NOT set saved.setRequestScripts(requestScripts) here.
        // saveAll() with a non-null @IdClass key uses em.merge(), which returns a NEW managed
        // instance — the originals become detached. Putting detached objects into the managed
        // VmRequest collection causes NonUniqueObjectException on the next auto-flush.
        // toResponse() receives the ordered IDs directly and never reads saved.requestScripts.

        // 7. Process and persist script parameters (for all scripts in resolved order, including auto-added).
        Map<String, Map<String, String>> userParams =
                request.getScriptParameters() != null ? request.getScriptParameters() : Map.of();

        // Track auto-generated values across scripts to prevent same-named parameters
        // (e.g. PORT on two different scripts) from landing on the same value.
        Map<String, Set<String>> usedAutoValues = new HashMap<>();

        for (UUID scriptId : orderedScriptIds) {
            Script script = scriptById.get(scriptId);
            Map<String, String> userValues = userParams.getOrDefault(scriptId.toString(), Map.of());

            // Derive randomise flags: USER_INPUT_OR_RANDOM params with no user value -> auto-generate.
            Map<String, Boolean> randomiseFlags = buildRandomiseFlagsForScript(scriptId, userValues);

            try {
                ParameterProcessingResult result = scriptParameterService.processParameters(
                        scriptId, userValues, randomiseFlags, usedAutoValues);

                List<VmRequestScriptParameter> params = new ArrayList<>();
                for (Map.Entry<String, String> entry : result.parameterValues().entrySet()) {
                    if (entry.getValue() == null) continue;
                    VmRequestScriptParameter param = new VmRequestScriptParameter();
                    param.setVmRequest(saved);
                    param.setScript(script);
                    param.setParameterName(entry.getKey());
                    param.setParameterValue(entry.getValue());
                    boolean wasAuto = Boolean.TRUE.equals(result.autoGeneratedFlags().get(entry.getKey()));
                    param.setWasAutoGenerated(wasAuto);
                    if (wasAuto) {
                        usedAutoValues.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                                .add(entry.getValue());
                    }
                    params.add(param);
                }
                scriptParameterRepository.saveAll(params);
            } catch (ParameterValidationException ex) {
                throw new ValidationException("Parameter validation failed", ex.getErrors());
            }
        }

        log.info("Build {} created by user {} for template {}", saved.getId(), user.getId(), template.getIdentifier());
        auditService.logBuildCreated(user, saved.getId(), template.getId(), ctx);
        return toResponse(saved, scripts, orderedScriptIds);
    }

    /**
     * Creates a new QUEUED build by copying the template, scripts, and user-provided
     * parameters from a previous failed build owned by the requesting user.
     * Auto-generated parameter values are not copied — they will be freshly randomised.
     * Throws 404 if the source build is not found or not owned by the user.
     * Throws 409 if the source build is not in FAILED state.
     */
    @Transactional
    public BuildResponse cloneBuild(UUID sourceBuildId, User user, AuditContext ctx) {
        VmRequest source = vmRequestRepository.findById(sourceBuildId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + sourceBuildId));
        if (!source.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Build not found: " + sourceBuildId);
        }
        if (source.getState() != VmRequestState.FAILED) {
            throw new ConflictException("Only failed builds can be retried (state: " + source.getState() + ")");
        }

        // Reconstruct user-requested script IDs, excluding auto-added dependencies
        // (createBuild will re-resolve dependencies from scratch).
        List<UUID> userScriptIds = vmRequestScriptRepository
                .findWithScriptByVmRequestId(sourceBuildId)
                .stream()
                .filter(vrs -> !Boolean.TRUE.equals(vrs.getAutoAdded()))
                .sorted(Comparator.comparingInt(VmRequestScript::getExecutionOrder))
                .map(VmRequestScript::getScriptId)
                .toList();

        // Reconstruct user-provided parameter values, skipping auto-generated ones
        // so they are freshly randomised in the new build.
        List<VmRequestScriptParameter> storedParams = scriptParameterRepository.findByVmRequest_Id(sourceBuildId);
        Map<String, Map<String, String>> scriptParameters = new HashMap<>();
        for (VmRequestScriptParameter p : storedParams) {
            if (Boolean.TRUE.equals(p.getWasAutoGenerated())) continue;
            scriptParameters
                    .computeIfAbsent(p.getScript().getId().toString(), k -> new HashMap<>())
                    .put(p.getParameterName(), p.getParameterValue());
        }

        CreateBuildRequest request = new CreateBuildRequest();
        request.setTemplateId(source.getTemplate().getId());
        request.setVulnerabilityIds(userScriptIds);
        request.setDifficulty(source.getDifficultyOverride());
        request.setTelemetryEnabled(Boolean.TRUE.equals(source.getTelemetryEnabled()));
        request.setScriptParameters(scriptParameters.isEmpty() ? null : scriptParameters);

        log.info("Cloning failed build {} for user {}", sourceBuildId, user.getId());
        return createBuild(user, request, ctx);
    }

    /**
     * Returns builds for the given user, most recent first, paginated.
     * Defaults to page 0, 20 items per page.
     */
    @Transactional(readOnly = true)
    public PagedResponse<BuildResponse> getUserBuilds(User user, int page, int size) {
        Page<VmRequest> paged = vmRequestRepository.findByUser_IdOrderBySubmittedAtDesc(
                user.getId(), PageRequest.of(page, size));
        List<BuildResponse> content = paged.getContent().stream()
                .map(r -> toResponse(r, loadScripts(r), getScriptOrderFromRequest(r)))
                .toList();
        return PagedResponse.from(paged, content);
    }

    /**
     * Returns a single build visible to the requesting user.
     * Throws 404 if not found, 403 if owned by another user.
     */
    @Transactional(readOnly = true)
    public BuildResponse getBuild(UUID requestId, User user) {
        VmRequest request = vmRequestRepository.findByIdWithTemplate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + requestId));
        if (!request.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Build not found: " + requestId);
        }
        List<Script> scripts = loadScripts(request);
        List<UUID> order = getScriptOrderFromRequest(request);
        return toResponse(request, scripts, order);
    }


    /**
     * Returns the OVA file metadata needed for a download response.
     * Throws 404 if not found, not owned by the user, not READY, or file deleted.
     */
    @Transactional(readOnly = true)
    public BuildDownloadInfo getBuildDownloadInfo(UUID requestId, User user, AuditContext ctx) {
        VmRequest request = vmRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + requestId));
        if (!request.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Build not found: " + requestId);
        }
        if (request.getState() == VmRequestState.ARCHIVED) {
            throw new GoneException("Build has been archived — the OVA is no longer available for download");
        }
        if (request.getState() != VmRequestState.READY) {
            throw new ConflictException("Build is not ready for download (state: " + request.getState() + ")");
        }
        if (request.getStorageDeletedAt() != null || request.getStoragePath() == null) {
            throw new GoneException("OVA file is no longer available");
        }
        auditService.logBuildDownloaded(user, requestId, ctx);
        return new BuildDownloadInfo(
                request.getStoragePath(),
                request.getOvaFilename(),
                request.getOvaSizeBytes() != null ? request.getOvaSizeBytes() : 0L,
                request.getOvaSha256()
        );
    }

    /**
     * Returns the provenance record for a completed build.
     * Throws 404 if not found, not owned by the user, or provenance not yet created.
     */
    @Transactional(readOnly = true)
    public ProvenanceResponse getBuildProvenance(UUID requestId, User user, AuditContext ctx) {
        VmRequest request = vmRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + requestId));
        if (!request.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Build not found: " + requestId);
        }
        ProvenanceRecord provenance = provenanceRecordRepository.findByVmRequest_Id(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Provenance not available for this build"));
        auditService.logProvenanceRetrieved(user, requestId, ctx);
        List<ProvenanceResponse.ScriptInfo> scriptInfos = provenance.getScripts().stream()
                .map(s -> ProvenanceResponse.ScriptInfo.builder()
                        .scriptId(s.getScriptId() != null ? s.getScriptId().toString() : null)
                        .identifier(s.getScriptIdentifier())
                        .version(s.getScriptVersion())
                        .sha256(s.getScriptSha256())
                        .executionOrder(s.getExecutionOrder())
                        .exitCode(s.getExitCode())
                        .durationSeconds(s.getDurationSeconds())
                        .build())
                .toList();

        return ProvenanceResponse.builder()
                .buildId(requestId)
                .templateIdentifier(provenance.getTemplateIdentifier())
                .templateVersion(provenance.getTemplateVersion())
                .templateSha256(provenance.getTemplateSha256())
                .buildSeed(provenance.getBuildSeed())
                .difficultyLevel(provenance.getDifficultyLevel())
                .telemetryEnabled(Boolean.TRUE.equals(provenance.getTelemetryEnabled()))
                .ovaSha256(provenance.getOvaSha256())
                .flagDerivationTimestamp(provenance.getFlagDerivationTimestamp())
                .finalisedAt(provenance.getFinalisedAt())
                .workerId(provenance.getWorkerId())
                .buildStartedAt(provenance.getBuildStartedAt())
                .buildFinishedAt(provenance.getBuildFinishedAt())
                .scripts(scriptInfos)
                .build();
    }

    /**
     * Returns a paginated list of all builds across all users — admin only.
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdminBuildSummaryDto> getAllBuilds(int page, int size) {
        Page<VmRequest> builds = vmRequestRepository.findAll(PageRequest.of(page, size,
                Sort.by("submittedAt").descending()));
        List<AdminBuildSummaryDto> items = builds.getContent().stream()
                .map(r -> AdminBuildSummaryDto.builder()
                        .id(r.getId())
                        .state(r.getState().name().toLowerCase())
                        .userId(r.getUser().getId())
                        .username(r.getUser().getUsername())
                        .templateId(r.getTemplate().getId())
                        .templateName(r.getTemplate().getIdentifier())
                        .difficulty(r.getDifficultyOverride())
                        .currentWorkerStage(r.getCurrentWorkerStage())
                        .failureReason(r.getFailureReason())
                        .ovaFilename(r.getOvaFilename())
                        .ovaSha256(r.getOvaSha256())
                        .ovaSizeBytes(r.getOvaSizeBytes())
                        .telemetryEnabled(Boolean.TRUE.equals(r.getTelemetryEnabled()))
                        .submittedAt(r.getSubmittedAt())
                        .completedAt(r.getCompletedAt())
                        .failedAt(r.getFailedAt())
                        .build())
                .toList();
        return PagedResponse.from(builds, items);
    }

    /**
     * Returns the VM login credentials for a completed build — admin only.
     * Throws 404 if the build has no provenance (not yet complete) or no password stored
     * (built before this feature was introduced).
     */
    @Transactional(readOnly = true)
    public AdminBuildCredentialsResponse getAdminBuildCredentials(UUID requestId) {
        VmRequest request = vmRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Build not found: " + requestId));
        ProvenanceRecord provenance = provenanceRecordRepository.findByVmRequest_Id(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Provenance not available for this build"));
        if (provenance.getVmLoginPassword() == null) {
            throw new ResourceNotFoundException("VM login credentials not available for this build");
        }
        return AdminBuildCredentialsResponse.builder()
                .buildId(requestId)
                .sshUsername(request.getTemplate().getExpectedSshUser())
                .vmLoginPassword(provenance.getVmLoginPassword())
                .build();
    }

    private Map<String, Boolean> buildRandomiseFlagsForScript(UUID scriptId, Map<String, String> userValues) {
        // Load parameters for this script and mark USER_INPUT_OR_RANDOM params without user values.
        List<ScriptParameterDto> params = scriptParameterService.listParameters(scriptId);
        Map<String, Boolean> flags = new HashMap<>();
        for (ScriptParameterDto p : params) {
            if (p.getValueSource() == USER_INPUT_OR_RANDOM) {
                String userVal = userValues.get(p.getParameterName());
                flags.put(p.getParameterName(), userVal == null || userVal.isBlank());
            }
        }
        return flags;
    }

    private List<Script> loadScripts(VmRequest request) {
        List<UUID> ids = request.getRequestScripts().stream()
                .map(VmRequestScript::getScriptId)
                .toList();
        if (ids.isEmpty()) return List.of();
        return scriptRepository.findAllById(ids);
    }

    private List<UUID> getScriptOrderFromRequest(VmRequest request) {
        return request.getRequestScripts().stream()
                .sorted(Comparator.comparingInt(VmRequestScript::getExecutionOrder))
                .map(VmRequestScript::getScriptId)
                .toList();
    }

    private BuildResponse toResponse(VmRequest r, List<Script> scripts, List<UUID> orderedIds) {
        Map<UUID, Script> byId = scripts.stream().collect(Collectors.toMap(Script::getId, s -> s));

        List<BuildScriptDto> scriptDtos = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            Script s = byId.get(id);
            scriptDtos.add(BuildScriptDto.builder()
                    .scriptId(id)
                    .scriptName(s != null ? s.getName() : id.toString())
                    .executionOrder(i + 1)
                    .build());
        }

        Template t = r.getTemplate();
        String templateName = t != null ? t.getIdentifier() + " " + t.getVersion() : "";

        return BuildResponse.builder()
                .id(r.getId())
                .state(r.getState().name().toLowerCase())
                .templateId(t != null ? t.getId() : null)
                .templateName(templateName)
                .scripts(scriptDtos)
                .difficulty(r.getDifficultyOverride())
                .telemetryEnabled(Boolean.TRUE.equals(r.getTelemetryEnabled()))
                .currentWorkerStage(r.getCurrentWorkerStage())
                .submittedAt(r.getSubmittedAt())
                .completedAt(r.getCompletedAt())
                .failedAt(r.getFailedAt())
                .failureReason(r.getFailureReason())
                .ovaFilename(r.getOvaFilename())
                .ovaSha256(r.getOvaSha256())
                .ovaSizeBytes(r.getOvaSizeBytes())
                .build();
    }

    private static String generateBuildSeed() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

}
