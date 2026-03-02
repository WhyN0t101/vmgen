package io.vmgen.api.controller;

import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.service.script.ScriptParameterService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scripts")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptParameterService parameterService;


    @GetMapping("/{id}/parameters")
    public ResponseEntity<List<ScriptParameterDto>> getUserInputParameters(@PathVariable UUID id) {
        return ResponseEntity.ok(parameterService.listUserInputParameters(id));
    }
}
