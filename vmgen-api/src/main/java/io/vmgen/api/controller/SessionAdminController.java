package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.admin.ActiveSessionDto;
import io.vmgen.api.dto.admin.RevokeAllSessionsRequest;
import io.vmgen.api.dto.admin.RevokeSessionRequest;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.User;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.service.AuditService;
import io.vmgen.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sessions")
@PreAuthorize("hasRole('ADMIN')")
public class SessionAdminController {
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public SessionAdminController(SessionService sessionService, UserRepository userRepository, AuditService auditService) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<List<ActiveSessionDto>> listActive() {
        return ResponseEntity.ok(sessionService.listActiveSessions());
    }

    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@Valid @RequestBody RevokeSessionRequest req, Authentication auth, HttpServletRequest http) {
        User admin = requireAdminUser(auth);
        UUID sessionId = req.sessionUuid();  // Calls the method that converts string to UUID

        Optional<UUID> targetUserId = sessionService.getSessionOwnerUserId(sessionId);
        sessionService.revokeSession(sessionId, admin.getId());

        targetUserId.ifPresent(userId ->
                auditService.logSessionRevoked(admin, userId, sessionId, auditContext(http, Map.of()))

        );

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<?> revokeAll(@Valid @RequestBody RevokeAllSessionsRequest req, Authentication auth, HttpServletRequest http) {
        User admin = requireAdminUser(auth);
        UUID userId = req.userUuid();
        sessionService.revokeAllSessionsForUser(userId);
        auditService.logSessionRevoked(admin, userId, null, auditContext(http, Map.of()));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private User requireAdminUser(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        assert principal != null;
        UUID adminId = UUID.fromString(principal.userId());
        return userRepository.findById(adminId).orElseThrow();
    }



    private static AuditContext auditContext(HttpServletRequest req, Map<String, Object> metadata) {
        String ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");
        return new AuditContext(ip, ua, metadata);
    }

}


