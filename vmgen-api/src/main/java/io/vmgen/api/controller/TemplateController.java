package io.vmgen.api.controller;

import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.script.ScriptListDto;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<PagedResponse<TemplateListResponse>> listActiveTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String osFamily
    ) {
        return ResponseEntity.ok(templateService.listActiveTemplates(page, size, search, osFamily));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        TemplateResponse response = templateService.getTemplate(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/scripts")
    public ResponseEntity<List<ScriptListDto>> getCompatibleScripts(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getCompatibleScripts(id));
    }
}
