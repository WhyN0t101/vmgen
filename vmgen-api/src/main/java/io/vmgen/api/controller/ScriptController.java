package io.vmgen.api.controller;

import io.vmgen.api.dto.script.ScriptParameterDto;
import io.vmgen.api.models.script.ValueSource;
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

    /**
     * Returns only the parameters that require explicit user input for the given script.
     * AUTO_RANDOM and AUTO_CHOICE parameters are resolved by the worker and are not shown.
     */
    @GetMapping("/{id}/parameters")
    public ResponseEntity<List<ScriptParameterDto>> getUserInputParameters(@PathVariable UUID id) {
        List<ScriptParameterDto> all = parameterService.listParameters(id);
        List<ScriptParameterDto> userFacing = all.stream()
                .filter(p -> p.getValueSource() == ValueSource.USER_INPUT
                        || p.getValueSource() == ValueSource.USER_INPUT_OR_RANDOM)
                .toList();
        return ResponseEntity.ok(userFacing);
    }
}
