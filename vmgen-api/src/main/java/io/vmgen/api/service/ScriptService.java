package io.vmgen.api.service;

import io.vmgen.api.dto.script.CreateScriptRequest;
import io.vmgen.api.dto.script.ScriptDetailDto;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.script.UpdateScriptRequest;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ScriptConflictException;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.vulnerability.VulnerabilityCategory;
import io.vmgen.api.repository.ScriptRepository;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.repository.VulnerabilityCategoryRepository;
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

/**
 * Application service for managing vulnerability scripts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScriptService {

    private final ScriptRepository scriptRepository;
    private final VulnerabilityCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final ScriptCompatibilityService compatibilityService;
    private final ScriptParameterService parameterService;

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
    public ScriptDetailDto create(CreateScriptRequest request, String adminUsername) {
        if (scriptRepository.existsByIdentifierAndVersion(request.getIdentifier(), request.getVersion())) {
            throw new ConflictException("Script " + request.getIdentifier() + "@" + request.getVersion() + " already exists");
        }

        VulnerabilityCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));

        Script script = new Script();
        script.setIdentifier(request.getIdentifier());
        script.setVersion(request.getVersion());
        buildScript(category, script, request.getName(), request.getDescription(), request.getDifficultyLevel(), request.getScriptContent(), request.getFlagLocation(), request.getFlagOwnership(), request.getFlagPermissions());
        var admin = userRepository.findByUsernameIgnoreCase(adminUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + adminUsername));
        script.setCreatedBy(admin);

        Script saved = scriptRepository.save(script);

        // Optional: create compatibility and parameter definitions atomically with the script.
        createCompatibilityAndParametersIfPresent(saved.getId(), request, admin.getId());

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
            for (var param : request.getParameters()) {
                if (param == null) continue;
                parameterService.createParameter(scriptId, param);
            }
        }
    }

    /**
     * Constructs a new {@link Script} entity from the request, category, and creator.
     */
    private void buildScript(VulnerabilityCategory category, Script script, String name, String description, String difficultyLevel, String scriptContent, String flagLocation, String flagOwnership, String flagPermissions) {
        script.setName(name);
        script.setDescription(description);
        script.setCategory(category);
        script.setDifficultyLevel(difficultyLevel);
        script.setScriptContent(scriptContent);
        script.setSha256Hash(DigestUtils.sha256Hex(scriptContent));
        script.setFlagLocation(flagLocation);
        script.setFlagOwnership(flagOwnership);
        script.setFlagPermissions(flagPermissions);
    }

    /**
     * Updates an existing script. Identifier and version are immutable.
     */
    @Transactional
    public ScriptDetailDto update(UUID id, UpdateScriptRequest request) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + id));

        VulnerabilityCategory category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + request.getCategoryId()));

        buildScript(category, script, request.getName(), request.getDescription(), request.getDifficultyLevel(), request.getScriptContent(), request.getFlagLocation(), request.getFlagOwnership(), request.getFlagPermissions());

        Script saved = scriptRepository.save(script);
        log.info("Updated script {}@{}", saved.getIdentifier(), saved.getVersion());
        return toDetailDto(saved);
    }

    /**
     * Soft-deprecates a script.
     */
    @Transactional
    public void deprecate(UUID id) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Script not found: " + id));
        if (!Boolean.TRUE.equals(script.getIsActive())) {
            throw new ConflictException("Script is already deprecated");
        }
        script.setIsActive(false);
        script.setDeprecatedAt(Instant.now());
        scriptRepository.save(script);
        log.info("Deprecated script {}@{}", script.getIdentifier(), script.getVersion());
    }

    /**
     * Maps a {@link Script} entity to a full detail DTO.
     * Placeholder empty lists are used for requirements pending entity introduction.
     */
    private ScriptListDto toListDto(Script script) {
        VulnerabilityCategory category = script.getCategory();
        return ScriptListDto.builder()
                .id(script.getId())
                .identifier(script.getIdentifier())
                .version(script.getVersion())
                .name(script.getName())
                .difficultyLevel(script.getDifficultyLevel())
                .isActive(script.getIsActive())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .categoryColor(category != null ? category.getColor() : null)
                .owaspMapping(category != null ? category.getOwaspMapping() : null)
                .createdAt(script.getCreatedAt())
                .build();
    }

    /**
     * Maps a {@link Script} entity to a full detail DTO.
     * Placeholder empty lists are used for requirements pending entity introduction.
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
                // Requirements to be populated when requirement entities are introduced
                .requiredOsFamilies(List.of())
                .requiredCapabilities(List.of())
                .minRamMb(null)
                .minDiskGb(null)
                .build();
    }
}

