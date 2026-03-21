package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.template.AddCompatibleScriptRequest;
import io.vmgen.api.dto.template.RegisterTemplateRequest;
import io.vmgen.api.dto.template.TemplateListResponse;
import io.vmgen.api.dto.template.TemplateResponse;
import io.vmgen.api.dto.template.UpdateTemplateMetadataRequest;
import io.vmgen.api.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static io.vmgen.api.utils.HttpUtils.auditContext;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequiredArgsConstructor
public class AdminTemplateController {
    
    private final TemplateService templateService;
    

    @PostMapping
    public ResponseEntity<TemplateResponse> registerTemplate(
            @Valid @RequestBody RegisterTemplateRequest request,
            Authentication authentication,
            HttpServletRequest http
    ) {
        UUID adminId = getAdminId(authentication);
        TemplateResponse response = templateService.registerTemplate(request, adminId, auditContext(http, Map.of()));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    public ResponseEntity<List<TemplateListResponse>> listTemplates(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(templateService.listTemplates(status));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{id}/validate")
    public ResponseEntity<TemplateResponse> validateTemplate(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        UUID adminId = getAdminId(authentication);
        TemplateResponse response = templateService.validateTemplate(id, adminId, auditContext(http, Map.of()));
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/deprecate")
    public ResponseEntity<Void> deprecateTemplate(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest http
    ) {
        UUID adminId = getAdminId(authentication);
        templateService.deprecateTemplate(id, adminId, auditContext(http, Map.of()));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/metadata")
    public ResponseEntity<TemplateResponse> updateMetadata(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateMetadataRequest request,
            Authentication authentication,
            HttpServletRequest http
    ) {
        UUID adminId = getAdminId(authentication);
        TemplateResponse response = templateService.updateMetadata(id, request, adminId, auditContext(http, Map.of()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/compatible-scripts")
    public ResponseEntity<List<ScriptListDto>> getCompatibleScripts(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getCompatibleScripts(id));
    }

    @PostMapping("/{id}/compatible-scripts")
    public ResponseEntity<Void> addCompatibleScript(
            @PathVariable UUID id,
            @Valid @RequestBody AddCompatibleScriptRequest request,
            Authentication authentication
    ) {
        UUID adminId = getAdminId(authentication);
        templateService.addCompatibleScript(id, request.getScriptId(), adminId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/compatible-scripts/{scriptId}")
    public ResponseEntity<Void> removeCompatibleScript(
            @PathVariable UUID id,
            @PathVariable UUID scriptId
    ) {
        templateService.removeCompatibleScript(id, scriptId);
        return ResponseEntity.noContent().build();
    }

    private UUID getAdminId(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) Objects.requireNonNull(
                authentication.getPrincipal(), "Authentication principal must not be null"
        );
        return UUID.fromString(principal.userId());
    }
}
