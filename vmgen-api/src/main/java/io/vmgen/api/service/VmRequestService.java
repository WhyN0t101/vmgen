package io.vmgen.api.service;

import io.vmgen.api.dto.build.BuildResponse;
import io.vmgen.api.dto.build.BuildScriptDto;
import io.vmgen.api.dto.build.CreateBuildRequest;
import io.vmgen.api.dto.script.ScriptSelectionResult;
import io.vmgen.api.exceptions.CircularDependencyException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ScriptConflictException;
import io.vmgen.api.exceptions.ValidationException;
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
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Validates the request, creates a QUEUED VmRequest, and persists all
     * script entries and parameter values. Returns the full BuildResponse.
     */
    @Transactional
    public BuildResponse createBuild(User user, CreateBuildRequest request) {
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

        // 5. Ensure all resolved scripts (including auto-added dependencies) exist and are active.
        List<Script> scripts = scriptRepository.findActiveByIdsWithCategory(orderedScriptIds);
        if (scripts.size() != orderedScriptIds.size()) {
            Set<UUID> found = scripts.stream().map(Script::getId).collect(Collectors.toSet());
            List<UUID> missing = orderedScriptIds.stream().filter(id -> !found.contains(id)).toList();
            throw new ValidationException("Validation failed",
                    List.of("One or more required dependency scripts are not found or inactive: " + missing));
        }

        // 6. Build the VmRequest — save it first to obtain the generated ID.
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

        for (UUID scriptId : orderedScriptIds) {
            Script script = scriptById.get(scriptId);
            Map<String, String> userValues = userParams.getOrDefault(scriptId.toString(), Map.of());

            // Derive randomise flags: USER_INPUT_OR_RANDOM params with no user value → auto-generate.
            Map<String, Boolean> randomiseFlags = buildRandomiseFlagsForScript(scriptId, userValues);

            try {
                ParameterProcessingResult result = scriptParameterService.processParameters(
                        scriptId, userValues, randomiseFlags);

                for (Map.Entry<String, String> entry : result.parameterValues().entrySet()) {
                    if (entry.getValue() == null) continue;
                    VmRequestScriptParameter param = new VmRequestScriptParameter();
                    param.setVmRequest(saved);
                    param.setScript(script);
                    param.setParameterName(entry.getKey());
                    param.setParameterValue(entry.getValue());
                    param.setWasAutoGenerated(
                            Boolean.TRUE.equals(result.autoGeneratedFlags().get(entry.getKey())));
                    scriptParameterRepository.save(param);
                }
            } catch (Exception ex) {
                throw new ValidationException("Parameter validation failed", List.of(ex.getMessage()));
            }
        }

        log.info("Build {} created by user {} for template {}", saved.getId(), user.getId(), template.getIdentifier());
        return toResponse(saved, scripts, orderedScriptIds);
    }

    /**
     * Returns all builds for the given user, most recent first.
     */
    @Transactional
    public List<BuildResponse> getUserBuilds(User user) {
        return vmRequestRepository.findByUser_IdOrderBySubmittedAtDesc(user.getId()).stream()
                .map(r -> toResponse(r, loadScripts(r), getScriptOrderFromRequest(r)))
                .toList();
    }

    /**
     * Returns a single build visible to the requesting user.
     * Throws 404 if not found, 403 if owned by another user.
     */
    @Transactional
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

    // ---- helpers ----

    private Map<String, Boolean> buildRandomiseFlagsForScript(UUID scriptId, Map<String, String> userValues) {
        // Load parameters for this script and mark USER_INPUT_OR_RANDOM params without user values.
        var params = scriptParameterService.listParameters(scriptId);
        Map<String, Boolean> flags = new HashMap<>();
        for (var p : params) {
            if (p.getValueSource() == io.vmgen.api.models.script.ValueSource.USER_INPUT_OR_RANDOM) {
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
                .sorted(java.util.Comparator.comparingInt(VmRequestScript::getExecutionOrder))
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
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
