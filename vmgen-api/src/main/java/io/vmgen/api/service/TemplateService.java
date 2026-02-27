package io.vmgen.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.config.ProxmoxApiConfig;
import io.vmgen.api.config.TemplateDefaults;
import io.vmgen.api.dto.proxmox.ProxmoxVmSummary;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.template.RegisterTemplateRequest;
import io.vmgen.api.dto.template.TemplateListResponse;
import io.vmgen.api.dto.template.TemplateResponse;
import io.vmgen.api.dto.template.UpdateTemplateMetadataRequest;
import io.vmgen.api.exceptions.ConflictException;
import io.vmgen.api.exceptions.DuplicateTemplateException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.TemplateNotFoundException;
import io.vmgen.api.exceptions.TemplateValidationException;
import io.vmgen.api.mapper.ScriptListDtoMapper;
import io.vmgen.api.models.script.Script;
import io.vmgen.api.models.template.ScriptCompatibility;
import io.vmgen.api.models.template.Template;
import io.vmgen.api.models.template.TemplateCapability;
import io.vmgen.api.models.template.TemplateStatus;
import io.vmgen.api.models.user.User;
import io.vmgen.api.repository.script.ScriptCompatibilityRepository;
import io.vmgen.api.repository.script.ScriptRepository;
import io.vmgen.api.repository.template.TemplateCapabilityRepository;
import io.vmgen.api.repository.template.TemplateRepository;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.service.script.ScriptCompatibilityService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateCapabilityRepository capabilityRepository;
    private final ScriptCompatibilityRepository scriptCompatibilityRepository;
    private final ScriptRepository scriptRepository;
    private final ScriptCompatibilityService scriptCompatibilityService;
    private final UserRepository userRepository;
    private final ProxmoxService proxmoxService;
    private final ObjectMapper objectMapper;
    private final ProxmoxApiConfig proxmoxConfig;

    /**
     * Registers a new template from an existing Proxmox VM.
     * Validates uniqueness and VM existence before persisting.
     */
    @Transactional
    public TemplateResponse registerTemplate(RegisterTemplateRequest request, UUID adminId) {
        // Validate everything before database interaction
        validateRegistrationRequest(request);

        User creator = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + adminId));

        // Build and save template
        Template template = buildTemplate(request, creator);
        template = templateRepository.save(template);

        // Save capabilities
        saveCapabilities(template, request.getCapabilities());

        log.info("Template registered: {} {} (ID: {}, VMID: {}, Node: {})",
                request.getIdentifier(), request.getVersion(), template.getId(),
                template.getProxmoxVmid(), template.getProxmoxNode());

        return toResponse(template);
    }

    /**
     * Validates uniqueness of identifier/version combination and confirms
     * the target Proxmox VM exists and is a Proxmox template before any
     * database interaction.
     */
    private void validateRegistrationRequest(RegisterTemplateRequest request) {
        if (templateRepository.findByIdentifierAndVersion(
                request.getIdentifier(), request.getVersion()).isPresent()) {
            throw new DuplicateTemplateException(request.getIdentifier(), request.getVersion());
        }

        String node = request.getProxmoxNode();
        if (node == null || node.isBlank()) {
            throw new TemplateValidationException("Proxmox node is required");
        }

        Integer vmid = request.getProxmoxVmid();
        assertVmExists(vmid, node);

        // Ensure we do not register the same Proxmox template twice
        if (templateRepository.existsByProxmoxVmidAndProxmoxNode(vmid, node)) {
            throw new TemplateValidationException(
                    "This Proxmox template (VMID " + vmid + " on node " + node + ") is already registered"
            );
        }

        // Ensure the backing Proxmox VM is actually a template (template == 1)
        ProxmoxVmSummary vmSummary = proxmoxService.getVmSummary(vmid, node)
                .orElseThrow(() -> new TemplateValidationException(
                        "Proxmox VM " + vmid + " does not exist on node " + node
                ));

        if (vmSummary.getIsTemplate() == null || !vmSummary.getIsTemplate()) {
            throw new TemplateValidationException(
                    "Proxmox VM " + vmid + " on node " + node + " is not a Proxmox template. " +
                            "Convert it to a template in Proxmox before registering."
            );
        }
    }

    /**
     * Asserts that a VM with the given VMID exists on the specified node.
     * Throws {@link TemplateValidationException} if the VM cannot be found.
     */
    private void assertVmExists(Integer vmid, String node) {
        if (!proxmoxService.vmExists(vmid, node)) {
            log.error("VM validation failed: VMID {} does not exist on node {}", vmid, node);
            throw new TemplateValidationException(
                    "Proxmox VM " + vmid + " does not exist on node " + node
            );
        }
    }


    /**
     * Constructs a new {@link Template} entity from the registration request,
     * applying defaults for optional fields where not provided.
     */
    private Template buildTemplate(RegisterTemplateRequest request, User creator) {
        Template template = new Template();
        template.setIdentifier(request.getIdentifier());
        template.setVersion(request.getVersion());
        template.setOsFamily(request.getOsFamily().toLowerCase());
        template.setOsVersion(request.getOsVersion());
        template.setArchitecture(request.getArchitecture().toLowerCase());
        template.setDescription(request.getDescription());
        template.setProxmoxVmid(request.getProxmoxVmid());
        template.setProxmoxNode(request.getProxmoxNode());
        template.setDiskStorage(request.getDiskStorage());
        template.setDiskSizeGb(request.getDiskSizeGb());
        template.setMinRamMb(request.getMinRamMb() != null ? request.getMinRamMb() : TemplateDefaults.MIN_RAM_MB);
        template.setMinCpuCores(request.getMinCpuCores() != null ? request.getMinCpuCores() : TemplateDefaults.MIN_CPU_CORES);
        template.setExpectedSshUser(request.getExpectedSshUser() != null ? request.getExpectedSshUser() : TemplateDefaults.SSH_USER);
        template.setExpectedSshPort(request.getExpectedSshPort() != null ? request.getExpectedSshPort() : TemplateDefaults.SSH_PORT);
        template.setNetworkBridge(request.getNetworkBridge());
        String sshAuthMethod = request.getSshAuthMethod();
        if (sshAuthMethod == null || sshAuthMethod.isBlank()) {
            sshAuthMethod = "cloudinit";
        }
        template.setSshAuthMethod(sshAuthMethod);
        template.setStatus(TemplateStatus.DRAFT);
        template.setCreatedBy(creator);
        return template;
    }


    /**
     * Persists the capability tags associated with a template.
     * No-ops if the capability list is null or empty.
     */
    private void saveCapabilities(Template template, List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return;
        }

        List<TemplateCapability> caps = capabilities.stream()
                .map(cap -> new TemplateCapability(template.getId(), cap, template))
                .collect(Collectors.toList());

        capabilityRepository.saveAll(caps);
    }

    /**
     * Retrieves a single template by its UUID.
     * Throws {@link TemplateNotFoundException} if no match exists.
     */
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + id));
        return toResponse(template);
    }


    /**
     * Returns all active templates ordered by creation date descending.
     * Intended for end-user consumption; excludes draft, failed, and deprecated templates.
     */
    @Transactional(readOnly = true)
    public List<TemplateListResponse> listActiveTemplates() {
        return templateRepository.findAllByStatusOrderByCreatedAtDesc(TemplateStatus.ACTIVE).stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }


    /**
     * Returns templates filtered by status, or all templates if status is null or blank.
     * Throws {@link TemplateValidationException} if the provided status string is not a valid {@link TemplateStatus}.
     */
    @Transactional(readOnly = true)
    public List<TemplateListResponse> listTemplates(String status) {
        if (status == null || status.isBlank()) {
            return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                    .map(this::toListResponse)
                    .collect(Collectors.toList());
        }

        TemplateStatus statusEnum;
        try {
            statusEnum = TemplateStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new TemplateValidationException("Invalid status: " + status);
        }

        return templateRepository.findAllByStatusOrderByCreatedAtDesc(statusEnum).stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * Transitions a template from DRAFT to ACTIVE after verifying its Proxmox VM state.
     * Sets the template to FAILED if Proxmox validation does not pass.
     * Throws {@link TemplateValidationException} if the template is not in DRAFT status.
     */
    @Transactional
    public TemplateResponse validateTemplate(UUID templateId, UUID adminId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + templateId));

        if (template.getStatus() != TemplateStatus.DRAFT) {
            throw new TemplateValidationException(
                    "Template must be in 'draft' status to validate. Current status: " + template.getStatus()
            );
        }

        // Validate Proxmox state before changing anything
        ValidationResult validation = performProxmoxValidation(template);

        if (!validation.valid()) {
            template.setStatus(TemplateStatus.FAILED);
            templateRepository.save(template);
            throw new TemplateValidationException(validation.errorMessage());
        }

        // All validation passed, update to active in single transaction
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminId));

        updateTemplateToActive(template, admin);
        template = templateRepository.save(template);

        log.info("Template validated: {} {} (ID: {}, VMID: {}, Node: {})",
                template.getIdentifier(), template.getVersion(), template.getId(),
                template.getProxmoxVmid(), template.getProxmoxNode());

        return toResponse(template);
    }


    /**
     * Performs Proxmox-side validation checks for a template.
     * Returns a {@link ValidationResult} rather than throwing, allowing the caller
     * to decide how to handle failure.
     */
    private ValidationResult performProxmoxValidation(Template template) {
        String node = template.getProxmoxNode();
        Integer vmid = template.getProxmoxVmid();
        String targetNode = (node != null && !node.isBlank()) ? node : proxmoxConfig.getNode();

        if (vmid == null) {
            return ValidationResult.failed("Template has no Proxmox VMID configured");
        }

        if (!proxmoxService.vmExists(vmid, targetNode)) {
            //noinspection LoggingSimilarMessage
            log.error("VM validation failed: VMID {} does not exist on node {}", vmid, targetNode);
            return ValidationResult.failed("Proxmox VM " + vmid + " does not exist on node " + targetNode);
        }

        try {
            proxmoxService.getVmDetails(vmid, targetNode);
        } catch (Exception e) {
            log.warn("Could not fetch VM details during validation for template {}: {}",
                    template.getId(), e.getMessage());
        }

        return ValidationResult.success();
    }


    /**
     * Applies the ACTIVE status and records audit metadata on the template.
     * Sets a placeholder SHA-256 hash pending full validation by the Automation Worker.
     */
    private void updateTemplateToActive(Template template, User admin) {
        template.setStatus(TemplateStatus.ACTIVE);

        Map<String, Object> validationLogs = new HashMap<>();
        validationLogs.put("registered", true);
        validationLogs.put("proxmox_verified", true);
        validationLogs.put("note", "Full validation (SSH, capabilities, hashing) will be performed by Automation Worker");

        try {
            template.setValidationLogs(objectMapper.writeValueAsString(validationLogs));
        } catch (Exception e) {
            log.error("Failed to serialize validation logs for template {}: {}",
                    template.getId(), e.getMessage());
        }

        template.setSha256Hash("pending-worker-validation-" + UUID.randomUUID().toString().replace("-", ""));
        template.setValidatedBy(admin);
        template.setValidatedAt(Instant.now());
        template.setLastValidatedAt(Instant.now());
    }

    /**
     * Marks a template as DEPRECATED and records the admin responsible.
     * Throws {@link TemplateValidationException} if the template is already deprecated.
     */
    @Transactional
    public void deprecateTemplate(UUID templateId, UUID adminId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + templateId));

        if (template.getStatus() == TemplateStatus.DEPRECATED) {
            throw new TemplateValidationException("Template is already deprecated");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminId));

        template.setStatus(TemplateStatus.DEPRECATED);
        template.setDeprecatedAt(Instant.now());
        template.setDeprecatedBy(admin);
        templateRepository.save(template);

        log.info("Template deprecated: {} v{} by admin {} (ID: {})",
                template.getIdentifier(), template.getVersion(), adminId, template.getId());
    }

    /**
     * Updates mutable metadata fields on a template.
     * Only non-null fields in the request are applied; immutable fields such as
     * identifier, version, and Proxmox configuration are not affected.
     */
    @Transactional
    public TemplateResponse updateMetadata(UUID id, UpdateTemplateMetadataRequest request) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + id));

        if (request.getDescription() != null) {
            template.setDescription(request.getDescription());
        }
        if (request.getExpectedSshUser() != null) {
            template.setExpectedSshUser(request.getExpectedSshUser());
        }
        if (request.getExpectedSshPort() != null) {
            template.setExpectedSshPort(request.getExpectedSshPort());
        }
        if (request.getNetworkBridge() != null) {
            template.setNetworkBridge(request.getNetworkBridge());
        }
        if (request.getMinRamMb() != null) {
            template.setMinRamMb(request.getMinRamMb());
        }
        if (request.getMinCpuCores() != null) {
            template.setMinCpuCores(request.getMinCpuCores());
        }

        template = templateRepository.save(template);

        log.info("Template metadata updated: {} v{} (ID: {})", template.getIdentifier(), template.getVersion(), template.getId());

        return toResponse(template);
    }

    // Helper methods


    /**
     * Maps a {@link Template} entity to a full {@link TemplateResponse} DTO,
     * including a separate query for associated capabilities.
     */
    private TemplateResponse toResponse(Template template) {
        List<String> capabilities = capabilityRepository.findAllByTemplateId(template.getId()).stream()
                .map(TemplateCapability::getCapability)
                .collect(Collectors.toList());

        return TemplateResponse.builder()
                .id(template.getId())
                .identifier(template.getIdentifier())
                .version(template.getVersion())
                .osFamily(template.getOsFamily())
                .osVersion(template.getOsVersion())
                .architecture(template.getArchitecture())
                .description(template.getDescription())
                .sha256Hash(template.getSha256Hash())
                .diskSizeGb(template.getDiskSizeGb())
                .minRamMb(template.getMinRamMb())
                .minCpuCores(template.getMinCpuCores())
                .validatedAt(template.getValidatedAt())
                .validatedBy(template.getValidatedBy() != null ? template.getValidatedBy().getId() : null)
                .deprecatedBy(template.getDeprecatedBy() != null ? template.getDeprecatedBy().getId() : null)
                .validationNotes(template.getValidationNotes())
                .createdBy(template.getCreatedBy() != null ? template.getCreatedBy().getId() : null)
                .createdAt(template.getCreatedAt())
                .deprecatedAt(template.getDeprecatedAt())
                .status(template.getStatus())
                .proxmoxVmid(template.getProxmoxVmid())
                .proxmoxNode(template.getProxmoxNode())
                .diskStorage(template.getDiskStorage())
                .validationLogs(template.getValidationLogs())
                .lastValidatedAt(template.getLastValidatedAt())
                .expectedSshUser(template.getExpectedSshUser())
                .expectedSshPort(template.getExpectedSshPort())
                .networkBridge(template.getNetworkBridge())
                .sshAuthMethod(template.getSshAuthMethod())
                .updatedAt(template.getUpdatedAt())
                .capabilities(capabilities)
                .build();
    }


    /**
     * Maps a {@link Template} entity to a lightweight {@link TemplateListResponse} DTO.
     * Used for list endpoints where full detail is not required.
     */
    private TemplateListResponse toListResponse(Template template) {
        return TemplateListResponse.builder()
                .id(template.getId())
                .identifier(template.getIdentifier())
                .version(template.getVersion())
                .osFamily(template.getOsFamily())
                .status(template.getStatus())
                .createdAt(template.getCreatedAt())
                .build();
    }

    /**
     * Returns the scripts compatible with the given template, enriched with
     * conflict and dependency information scoped to the compatible set.
     * If no compatibility entries have been configured for the template,
     * all active scripts are returned so that newly-registered templates
     * remain usable on the Generate page.
     */
    @Transactional(readOnly = true)
    public List<ScriptListDto> getCompatibleScripts(UUID templateId) {
        if (!templateRepository.existsById(templateId)) {
            throw new TemplateNotFoundException("Template not found: " + templateId);
        }

        List<ScriptCompatibility> entries = scriptCompatibilityRepository.findAllByTemplateId(templateId);

        List<Script> scripts;
        if (entries.isEmpty()) {
            scripts = scriptRepository.findAllActiveWithCategory();
        } else {
            List<UUID> scriptIds = entries.stream()
                    .map(ScriptCompatibility::getScriptId)
                    .collect(Collectors.toList());
            scripts = scriptRepository.findActiveByIdsWithCategory(scriptIds);
        }

        return scriptCompatibilityService.getEnrichedForScripts(scripts);
    }

    /**
     * Marks a script as compatible with a template.
     * Throws {@link ConflictException} if the link already exists.
     */
    @Transactional
    public void addCompatibleScript(UUID templateId, UUID scriptId, UUID adminId) {
        if (!templateRepository.existsById(templateId)) {
            throw new TemplateNotFoundException("Template not found: " + templateId);
        }
        if (!scriptRepository.existsById(scriptId)) {
            throw new ResourceNotFoundException("Script not found: " + scriptId);
        }
        if (scriptCompatibilityRepository.existsByScriptIdAndTemplateId(scriptId, templateId)) {
            throw new ConflictException("Script is already marked compatible with this template");
        }

        ScriptCompatibility entry = new ScriptCompatibility(
                scriptId, templateId, Instant.now(), adminId, null, null);
        scriptCompatibilityRepository.save(entry);

        log.info("Script {} linked as compatible with template {} by admin {}", scriptId, templateId, adminId);
    }

    /**
     * Removes a script–template compatibility link.
     * Throws {@link ResourceNotFoundException} if the link does not exist.
     */
    @Transactional
    public void removeCompatibleScript(UUID templateId, UUID scriptId) {
        if (!scriptCompatibilityRepository.existsByScriptIdAndTemplateId(scriptId, templateId)) {
            throw new ResourceNotFoundException(
                    "Script " + scriptId + " is not linked to template " + templateId);
        }
        scriptCompatibilityRepository.deleteByScriptIdAndTemplateId(scriptId, templateId);

        log.info("Script {} unlinked from template {}", scriptId, templateId);
    }

    /**
     * Maps a {@link Script} entity to a {@link ScriptListDto} without conflict/dependency metadata.
     */
    public static ScriptListDto getScriptListDto(Script script) {
        return ScriptListDtoMapper.toBase(script);
    }

    // Validation result holder
        private record ValidationResult(boolean valid, String errorMessage) {

        static ValidationResult success() {
                return new ValidationResult(true, null);
            }

            static ValidationResult failed(String message) {
                return new ValidationResult(false, message);
            }
        }
}