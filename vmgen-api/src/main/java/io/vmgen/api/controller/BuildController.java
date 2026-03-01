package io.vmgen.api.controller;

import io.vmgen.api.dto.build.BuildResponse;
import io.vmgen.api.dto.build.CreateBuildRequest;
import io.vmgen.api.dto.common.PagedResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.service.UserService;
import io.vmgen.api.service.VmRequestService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/builds")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
@RequiredArgsConstructor
public class BuildController {

    private final VmRequestService vmRequestService;
    private final UserService userService;


    @PostMapping
    public ResponseEntity<BuildResponse> createBuild(
            @Valid @RequestBody CreateBuildRequest request,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        BuildResponse response = vmRequestService.createBuild(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<BuildResponse>> getUserBuilds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(vmRequestService.getUserBuilds(user, page, size));
    }


    @GetMapping("/{id}")
    public ResponseEntity<BuildResponse> getBuild(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        User user = userService.requireUser(authentication);
        return ResponseEntity.ok(vmRequestService.getBuild(id, user));
    }
}
