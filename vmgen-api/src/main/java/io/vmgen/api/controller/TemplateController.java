package io.vmgen.api.controller;

import io.vmgen.api.dto.template.TemplateListResponse;
import io.vmgen.api.dto.template.TemplateResponse;
import io.vmgen.api.service.TemplateService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class TemplateController {
    
    private final TemplateService templateService;
    
    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }
    
    @GetMapping
    public ResponseEntity<List<TemplateListResponse>> listActiveTemplates() {
        List<TemplateListResponse> templates = templateService.listActiveTemplates();
        return ResponseEntity.ok(templates);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(response);
    }
}
