package io.vmgen.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.config.TemplateDefaults;
import io.vmgen.api.dto.template.RegisterTemplateRequest;
import io.vmgen.api.dto.template.TemplateListResponse;
import io.vmgen.api.dto.template.TemplateResponse;
import io.vmgen.api.dto.template.UpdateTemplateMetadataRequest;
import io.vmgen.api.exceptions.DuplicateTemplateException;
import io.vmgen.api.exceptions.TemplateNotFoundException;
import io.vmgen.api.exceptions.TemplateValidationException;
import io.vmgen.api.models.Template;
import io.vmgen.api.models.TemplateCapability;
import io.vmgen.api.models.TemplateStatus;
import io.vmgen.api.models.User;
import io.vmgen.api.repository.TemplateCapabilityRepository;
import io.vmgen.api.repository.TemplateRepository;
import io.vmgen.api.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final ProxmoxService proxmoxService;
    private final ObjectMapper objectMapper;

    /**
     * Register a new template from an existing Proxmox VM.
     */
    @Transactional
    public TemplateResponse registerTemplate(RegisterTemplateRequest request, UUID userId) {
        // Validate everything before database interaction
        validateRegistrationRequest(request);

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

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
    }
    private void assertVmExists(Integer vmid, String node) {
        if (!proxmoxService.vmExists(vmid, node)) {
            log.error("VM validation failed: VMID {} does not exist on node {}", vmid, node);
            throw new TemplateValidationException(
                    "Proxmox VM " + vmid + " does not exist on node " + node
            );
        }
    }
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
        template.setStatus(TemplateStatus.DRAFT);
        template.setCreatedBy(creator);
        return template;
    }

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
     * Get a template by ID.
     */
    @Transactional(readOnly = true)
    public TemplateResponse getTemplate(UUID id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + id));
        return toResponse(template);
    }

    /**
     * List active templates (for users).
     */
    @Transactional(readOnly = true)
    public List<TemplateListResponse> listActiveTemplates() {
        return templateRepository.findAllByStatusOrderByCreatedAtDesc(TemplateStatus.ACTIVE).stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * List all templates (admin only).
     */
    @Transactional(readOnly = true)
    public List<TemplateListResponse> listAllTemplates() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toListResponse)
                .collect(Collectors.toList());
    }

    /**
     * List templates by status (admin filtering).
     */
    @Transactional(readOnly = true)
    public List<TemplateListResponse> listTemplatesByStatus(String status) {
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
     * Validate a template (transition from draft to active).
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

    private ValidationResult performProxmoxValidation(Template template) {
        String node = template.getProxmoxNode();
        Integer vmid = template.getProxmoxVmid();

        if (node == null || node.isBlank()) {
            return ValidationResult.failed("Template has no Proxmox node configured");
        }

        if (vmid == null) {
            return ValidationResult.failed("Template has no Proxmox VMID configured");
        }

        assertVmExists(vmid, node);

        try {
            proxmoxService.getVmDetails(vmid, node);
        } catch (Exception e) {
            log.warn("Could not fetch VM details during validation for template {}: {}",
                    template.getId(), e.getMessage());
        }

        return ValidationResult.success();
    }

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
     * Deprecate a template (soft delete).
     */
    @Transactional
    public void deprecateTemplate(UUID templateId, UUID adminId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new TemplateNotFoundException("Template not found: " + templateId));

        template.setStatus(TemplateStatus.DEPRECATED);
        template.setDeprecatedAt(Instant.now());
        templateRepository.save(template);

        log.info("Template deprecated: {} v{} (ID: {})", template.getIdentifier(), template.getVersion(), template.getId());
    }

    /**
     * Update template metadata (only mutable fields).
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
                .updatedAt(template.getUpdatedAt())
                .capabilities(capabilities)
                .build();
    }

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