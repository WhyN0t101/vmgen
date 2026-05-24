package io.vmgen.api.service.script;

import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.script.*;
import io.vmgen.api.mapper.ScriptListDtoMapper;
import io.vmgen.api.service.AuditService;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ScriptConflictException;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.script.ScriptCapability;
import io.vmgen.api.models.script.ScriptRequirement;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.vulnerability.VulnerabilityCategory;
import io.vmgen.api.repository.script.ScriptRepository;
import io.vmgen.api.repository.script.ScriptCapabilityRepository;
import io.vmgen.api.repository.script.ScriptRequirementRepository;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.repository.vm.VulnerabilityCategoryRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final ScriptRequirementRepository scriptRequirementRepository;
    private final ScriptCapabilityRepository scriptCapabilityRepository;
    private final VulnerabilityCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ScriptCompatibilityService compatibilityService;
    private final ScriptParameterService parameterService;
    private final AuditService auditService;

    /**
     * Lists scripts with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<ScriptListDto> list(UUID categoryId, String difficulty, boolean activeOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        // Start from a no-op (always true) specification so we never pass null to the repository.
        Specification<Script> spec = (root, query, cb) -> cb.conjunction();

        if (activeOnly) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (difficulty != null && !difficulty.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("difficultyLevel"), difficulty));
        }

        return scriptRepository.findAll(spec, pageable).map(this::toListDto);
    }

    /**
     * Returns detailed information for a script.
     */
    @Transactional(readOnly = true)
    public ScriptDetailDto getDetail(UUID id) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + id));
        return toDetailDto(script);
    }

    /**
     * Creates a new script.
     */
    @Transactional
    public ScriptDetailDto create(CreateScriptRequest request, String adminUsername, AuditContext ctx) {
        if (scriptRepository.existsByIdentifierAndVersion(request.getIdentifier(), request.getVersion())) {
            throw new ConflictException("Script " + request.getIdentifier() + "@" + request.getVersion() + " already exists");
        }

        VulnerabilityCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));

        Script script = new Script();
        script.setIdentifier(request.getIdentifier());
        script.setVersion(request.getVersion());
        buildScript(category, script, request.getName(), request.getDescription(), request.getDifficultyLevel(), request.getScriptContent(), request.getFlagLocation(), request.getFlagOwnership(), request.getFlagPermissions(), request.getMinRamMb(), request.getMinDiskGb());
        User admin = userRepository.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + adminUsername));
        script.setCreatedBy(admin);

        Script saved = scriptRepository.save(script);

        syncScriptConstraints(saved.getId(), request.getRequiredOsFamilies(), request.getRequiredCapabilities());

        // Optional: create compatibility and parameter definitions atomically with the script.
        createCompatibilityAndParametersIfPresent(saved.getId(), request, admin.getId());

        auditService.logScriptCreated(admin, saved.getId(), saved.getIdentifier(), ctx);
        log.info("Created script {}@{}", saved.getIdentifier(), saved.getVersion());
        return toDetailDto(saved);
    }

    private void createCompatibilityAndParametersIfPresent(UUID scriptId, CreateScriptRequest request, UUID adminId) {
        List<UUID> conflictIds = request.getConflictsWithScriptIds();
        List<UUID> requiresIds = request.getRequiresScriptIds();

        if (conflictIds != null && !conflictIds.isEmpty()
                && requiresIds != null && !requiresIds.isEmpty()) {
            Set<UUID> overlaps = new HashSet<>(conflictIds);
            overlaps.retainAll(new HashSet<>(requiresIds));
            if (!overlaps.isEmpty()) {
                throw new ScriptConflictException("A script cannot be both a dependency and a conflict");
            }
        }

        if (conflictIds != null) {
            // De-dupe while preserving input order.
            Set<UUID> seen = new HashSet<>();
            for (UUID id : conflictIds) {
                if (id == null || !seen.add(id)) continue;
                compatibilityService.addConflict(scriptId, id, adminId);
            }
        }

        if (requiresIds != null) {
            Set<UUID> seen = new HashSet<>();
            for (UUID id : requiresIds) {
                if (id == null || !seen.add(id)) continue;
                compatibilityService.addDependency(scriptId, id, adminId);
            }
        }

        if (request.getParameters() != null) {
            for (CreateScriptParameterRequest param : request.getParameters()) {
                if (param == null) continue;
                parameterService.createParameter(scriptId, param);
            }
        }
    }

    /**
     * Constructs a new {@link Script} entity from the request, category, and creator.
     */
    private void buildScript(VulnerabilityCategory category, Script script, String name, String description, String difficultyLevel, String scriptContent, String flagLocation, String flagOwnership, String flagPermissions, Integer minRamMb, Integer minDiskGb) {
        script.setName(name);
        script.setDescription(description);
        script.setCategory(category);
        script.setDifficultyLevel(difficultyLevel);
        script.setScriptContent(scriptContent);
        script.setSha256Hash(DigestUtils.sha256Hex(scriptContent));
        script.setFlagLocation(flagLocation);
        script.setFlagOwnership(flagOwnership);
        script.setFlagPermissions(flagPermissions);
        script.setMinRamMb(minRamMb);
        script.setMinDiskGb(minDiskGb);
    }

    /**
     * Updates an existing script. Identifier and version are immutable.
     */
    @Transactional
    public ScriptDetailDto update(UUID id, UpdateScriptRequest request, User admin, AuditContext ctx) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + id));

        VulnerabilityCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));

        buildScript(category, script, request.getName(), request.getDescription(), request.getDifficultyLevel(), request.getScriptContent(), request.getFlagLocation(), request.getFlagOwnership(), request.getFlagPermissions(), request.getMinRamMb(), request.getMinDiskGb());

        Script saved = scriptRepository.save(script);
        syncScriptConstraints(saved.getId(), request.getRequiredOsFamilies(), request.getRequiredCapabilities());
        auditService.logScriptUpdated(admin, saved.getId(), ctx);
        log.info("Updated script {}@{}", saved.getIdentifier(), saved.getVersion());
        return toDetailDto(saved);
    }

    /**
     * Soft-deprecates a script.
     */
    @Transactional
    public void deprecate(UUID id, User admin, AuditContext ctx) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + id));
        if (!Boolean.TRUE.equals(script.getIsActive())) {
            throw new ConflictException("Script is already deprecated");
        }
        script.setIsActive(false);
        script.setDeprecatedAt(Instant.now());
        scriptRepository.save(script);
        auditService.logScriptDeprecated(admin, script.getId(), ctx);
        log.info("Deprecated script {}@{}", script.getIdentifier(), script.getVersion());
    }

    private void syncScriptConstraints(UUID scriptId, List<String> osFamilies, List<String> capabilities) {
        scriptRequirementRepository.deleteByScriptId(scriptId);
        scriptCapabilityRepository.deleteByScriptId(scriptId);

        if (osFamilies != null) {
            for (String family : osFamilies) {
                if (family == null || family.isBlank()) continue;
                ScriptRequirement req = new ScriptRequirement();
                req.setScriptId(scriptId);
                req.setRequirementType(ScriptRequirement.TYPE_OS_FAMILY);
                req.setRequirementValue(family.trim().toLowerCase());
                scriptRequirementRepository.save(req);
            }
        }

        if (capabilities != null) {
            for (String capability : capabilities) {
                if (capability == null || capability.isBlank()) continue;
                scriptCapabilityRepository.save(new ScriptCapability(scriptId, capability.trim()));
            }
        }
    }

    private List<String> loadRequiredOsFamilies(UUID scriptId) {
        return scriptRequirementRepository.findByScriptId(scriptId).stream()
                .filter(r -> ScriptRequirement.TYPE_OS_FAMILY.equals(r.getRequirementType()))
                .map(ScriptRequirement::getRequirementValue)
                .toList();
    }

    private List<String> loadRequiredCapabilities(UUID scriptId) {
        return scriptCapabilityRepository.findByScriptId(scriptId).stream()
                .map(ScriptCapability::getCapability)
                .toList();
    }

    /**
     * Maps a {@link Script} entity to a lightweight list DTO.
     */
    private ScriptListDto toListDto(Script script) {
        return ScriptListDtoMapper.toBase(script);
    }

    /**
     * Maps a {@link Script} entity to a full detail DTO.
     */
    private ScriptDetailDto toDetailDto(Script script) {
        VulnerabilityCategory category = script.getCategory();

        return ScriptDetailDto.builder()
                .id(script.getId())
                .identifier(script.getIdentifier())
                .version(script.getVersion())
                .name(script.getName())
                .description(script.getDescription())
                .difficultyLevel(script.getDifficultyLevel())
                .isActive(script.getIsActive())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryColor(category != null ? category.getColor() : null)
                .owaspMapping(category != null ? category.getOwaspMapping() : null)
                .scriptContent(script.getScriptContent())
                .flagLocation(script.getFlagLocation())
                .flagOwnership(script.getFlagOwnership())
                .flagPermissions(script.getFlagPermissions())
                .createdAt(script.getCreatedAt())
                .createdByUsername(script.getCreatedBy() != null ? script.getCreatedBy().getUsername() : null)
                .deprecatedAt(script.getDeprecatedAt())
                .requiredOsFamilies(loadRequiredOsFamilies(script.getId()))
                .requiredCapabilities(loadRequiredCapabilities(script.getId()))
                .minRamMb(script.getMinRamMb())
                .minDiskGb(script.getMinDiskGb())
                .build();
    }
}

