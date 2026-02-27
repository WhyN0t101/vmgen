package io.vmgen.api.controller;

import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.dto.script.AddScriptConflictRequest;
import io.vmgen.api.dto.script.AddScriptDependencyRequest;
import io.vmgen.api.dto.script.CreateScriptParameterRequest;
import io.vmgen.api.dto.script.CreateScriptRequest;
import io.vmgen.api.dto.script.ScriptCompatibilityViewDto;
import io.vmgen.api.dto.script.ScriptDetailDto;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.dto.script.UpdateScriptParameterRequest;
import io.vmgen.api.dto.script.UpdateScriptRequest;
import io.vmgen.api.service.ScriptCompatibilityService;
import io.vmgen.api.service.ScriptParameterService;
import io.vmgen.api.service.ScriptService;
import io.vmgen.api.service.UserService;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/scripts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminScriptController {

    private final ScriptService scriptService;
    private final UserService userService;
    private final ScriptCompatibilityService compatibilityService;
    private final ScriptParameterService parameterService;

    @GetMapping
    public ResponseEntity<PagedResponse<ScriptListDto>> listScripts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ScriptListDto> result = scriptService.list(categoryId, difficulty, activeOnly, page, size);
        PagedResponse<ScriptListDto> body = new PagedResponse<>(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScriptDetailDto> getScript(@PathVariable UUID id) {
        return ResponseEntity.ok(scriptService.getDetail(id));
    }

    @PostMapping
    public ResponseEntity<ScriptDetailDto> createScript(
            @Valid @RequestBody CreateScriptRequest request,
            Authentication authentication
    ) {
        String adminUsername = userService.requireAdminUser(authentication).getUsername();
        ScriptDetailDto created = scriptService.create(request, adminUsername);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScriptDetailDto> updateScript(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScriptRequest request
    ) {
        return ResponseEntity.ok(scriptService.update(id, request));
    }

    @PostMapping("/{id}/deprecate")
    public ResponseEntity<StatusResponse> deprecateScript(@PathVariable UUID id) {
        scriptService.deprecate(id);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }


    @GetMapping("/{id}/compatibility")
    public ResponseEntity<ScriptCompatibilityViewDto> getCompatibility(@PathVariable UUID id) {
        return ResponseEntity.ok(compatibilityService.getCompatibilityView(id));
    }

    @PostMapping("/{id}/conflicts")
    public ResponseEntity<StatusResponse> addConflict(
            @PathVariable UUID id,
            @Valid @RequestBody AddScriptConflictRequest request,
            Authentication authentication
    ) {
        UUID adminId = userService.requireAdminUser(authentication).getId();
        compatibilityService.addConflict(id, request.getConflictsWithScriptId(), adminId);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }


    @DeleteMapping("/{id}/conflicts/{conflictId}")
    public ResponseEntity<StatusResponse> removeConflict(
            @PathVariable UUID id,
            @PathVariable UUID conflictId
    ) {
        compatibilityService.removeConflict(id, conflictId);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @PostMapping("/{id}/dependencies")
    public ResponseEntity<StatusResponse> addDependency(
            @PathVariable UUID id,
            @Valid @RequestBody AddScriptDependencyRequest request,
            Authentication authentication
    ) {
        UUID adminId = userService.requireAdminUser(authentication).getId();
        compatibilityService.addDependency(id, request.getRequiresScriptId(), adminId);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }


    @DeleteMapping("/{id}/dependencies/{dependencyId}")
    public ResponseEntity<StatusResponse> removeDependency(
            @PathVariable UUID id,
            @PathVariable UUID dependencyId
    ) {
        compatibilityService.removeDependency(id, dependencyId);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @GetMapping("/{id}/parameters")
    public ResponseEntity<List<ScriptParameterDto>> listParameters(@PathVariable UUID id) {
        return ResponseEntity.ok(parameterService.listParameters(id));
    }

    @PostMapping("/{id}/parameters")
    public ResponseEntity<ScriptParameterDto> createParameter(
            @PathVariable UUID id,
            @Valid @RequestBody CreateScriptParameterRequest request
    ) {
        ScriptParameterDto created = parameterService.createParameter(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{scriptId}/parameters/{parameterId}")
    public ResponseEntity<ScriptParameterDto> updateParameter(
            @PathVariable UUID scriptId,
            @PathVariable UUID parameterId,
            @Valid @RequestBody UpdateScriptParameterRequest request
    ) {
        return ResponseEntity.ok(parameterService.updateParameter(scriptId, parameterId, request));
    }

    @DeleteMapping("/{scriptId}/parameters/{parameterId}")
    public ResponseEntity<StatusResponse> deleteParameter(
            @PathVariable UUID scriptId,
            @PathVariable UUID parameterId
    ) {
        parameterService.deleteParameter(scriptId, parameterId);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }
}

