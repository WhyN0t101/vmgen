package io.vmgen.api.controller;

import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.dto.script.CreateScriptRequest;
import io.vmgen.api.dto.script.ScriptDetailDto;
import io.vmgen.api.dto.script.ScriptListDto;
import io.vmgen.api.dto.script.UpdateScriptRequest;
import io.vmgen.api.service.ScriptService;
import io.vmgen.api.service.UserService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
}

