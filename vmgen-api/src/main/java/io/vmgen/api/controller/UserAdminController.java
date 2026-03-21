package io.vmgen.api.controller;

import io.vmgen.api.dto.admin.RoleUpdateRequest;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.user.UserRole;
import io.vmgen.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.vmgen.api.utils.HttpUtils.auditContext;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {
    private final UserService userService;


    @PostMapping("/{userId}/role")
    public ResponseEntity<StatusResponse> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest req,
            Authentication auth,
            HttpServletRequest http
    ) {
        User admin = userService.requireAdminUser(auth);
        userService.updateRole(admin, userId, UserRole.valueOf(req.role()), auditContext(http, Map.of()));
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

}


