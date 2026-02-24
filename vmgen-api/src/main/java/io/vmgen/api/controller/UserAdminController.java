package io.vmgen.api.controller;

import io.vmgen.api.dto.admin.RoleUpdateRequest;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.User;
import io.vmgen.api.models.UserRole;
import io.vmgen.api.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{userId}/role")
    public ResponseEntity<Map<String, String>> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleUpdateRequest req,
            Authentication auth,
            HttpServletRequest http
    ) {
        User admin = userService.requireAdminUser(auth);
        userService.updateRole(admin, userId, UserRole.valueOf(req.role()), auditContext(http, Map.of()));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }


    private static AuditContext auditContext(HttpServletRequest req, Map<String, Object> metadata) {
        String ip = getClientIp(req);
        String ua = req.getHeader("User-Agent");
        return new AuditContext(ip, ua, metadata);
    }

    /**
     * Extract client IP address, handling X-Forwarded-For header for proxies.
     */
    private static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}


