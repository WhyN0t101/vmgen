package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.template.RegisterTemplateRequest;
import io.vmgen.api.dto.template.TemplateListResponse;
import io.vmgen.api.dto.template.TemplateResponse;
import io.vmgen.api.dto.template.UpdateTemplateMetadataRequest;
import io.vmgen.api.service.TemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/templates")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTemplateController {
    
    private final TemplateService templateService;
    
    public AdminTemplateController(
            TemplateService templateService
    ) {
        this.templateService = templateService;
    }
    
    @PostMapping
    public ResponseEntity<TemplateResponse> registerTemplate(
            @Valid @RequestBody RegisterTemplateRequest request,
            Authentication authentication
    ) {
        UUID userId = getUserId(authentication);
        TemplateResponse response = templateService.registerTemplate(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<TemplateListResponse>> listTemplates(
            @RequestParam(required = false) String status
    ) {
        List<TemplateListResponse> templates;
        if (status != null && !status.isBlank()) {
            templates = templateService.listTemplatesByStatus(status);
        } else {
            templates = templateService.listAllTemplates();
        }
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/validate")
    public ResponseEntity<TemplateResponse> validateTemplate(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID adminId = getUserId(authentication);
        TemplateResponse response = templateService.validateTemplate(id, adminId);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{id}/deprecate")
    public ResponseEntity<Void> deprecateTemplate(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        UUID adminId = getUserId(authentication);
        templateService.deprecateTemplate(id, adminId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{id}/metadata")
    public ResponseEntity<TemplateResponse> updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateMetadataRequest request
    ) {
        TemplateResponse response = templateService.updateMetadata(id, request);
        return ResponseEntity.ok(response);
    }
    
    private UUID getUserId(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return UUID.fromString(principal.userId());
    }
}
