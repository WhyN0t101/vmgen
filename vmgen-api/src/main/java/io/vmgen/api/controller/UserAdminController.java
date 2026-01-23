package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.admin.RoleUpdateRequest;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.User;
import io.vmgen.api.models.UserRole;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserService userService;
    private final UserRepository userRepository;

    public UserAdminController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @PostMapping("/{userId}/role")
    public ResponseEntity<?> updateRole(
            @PathVariable String userId,
            @Valid @RequestBody RoleUpdateRequest req,
            Authentication auth,
            HttpServletRequest http
    ) {
        User admin = requireAdminUser(auth);
        UUID targetUserId = UUID.fromString(userId);
        UserRole newRole = UserRole.valueOf(req.role());
        userService.updateRole(admin, targetUserId, newRole, auditContext(http, Map.of()));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private User requireAdminUser(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        UUID adminId = UUID.fromString(Objects.requireNonNull(principal).userId());
        return userRepository.findById(adminId).orElseThrow();
    }

    private static AuditContext auditContext(HttpServletRequest req, Map<String, Object> metadata) {
        String ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");
        return new AuditContext(ip, ua, metadata);
    }
}


