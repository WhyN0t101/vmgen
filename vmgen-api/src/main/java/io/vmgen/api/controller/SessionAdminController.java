package io.vmgen.api.controller;

import io.vmgen.api.dto.admin.ActiveSessionDto;
import io.vmgen.api.dto.admin.RevokeAllSessionsRequest;
import io.vmgen.api.dto.admin.RevokeSessionRequest;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.models.user.User;
import io.vmgen.api.service.AuditService;
import io.vmgen.api.service.SessionService;
import io.vmgen.api.service.UserService;
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

import static io.vmgen.api.utils.HttpUtils.auditContext;

@RestController
@RequestMapping("/api/admin/sessions")
@PreAuthorize("hasRole('ADMIN')")
public class SessionAdminController {
    private final SessionService sessionService;
    private final UserService userService;
    private final AuditService auditService;

    public SessionAdminController(SessionService sessionService, AuditService auditService, UserService userService) {
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<ActiveSessionDto>> listActive() {
        return ResponseEntity.ok(sessionService.listActiveSessions());
    }

    @PostMapping("/revoke")
    public ResponseEntity<StatusResponse> revoke(@Valid @RequestBody RevokeSessionRequest req, Authentication auth, HttpServletRequest http) {
        User admin = userService.requireAdminUser(auth);
        UUID sessionId = req.sessionUuid();

        Optional<UUID> targetUserId = sessionService.getSessionOwnerUserId(sessionId);
        sessionService.revokeSession(sessionId, admin.getId());

        targetUserId.ifPresent(userId ->
                auditService.logSessionRevoked(admin, userId, sessionId, auditContext(http, Map.of()))
        );

        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<StatusResponse> revokeAll(@Valid @RequestBody RevokeAllSessionsRequest req, Authentication auth, HttpServletRequest http) {
        User admin = userService.requireAdminUser(auth);
        UUID userId = req.userUuid();
        sessionService.revokeAllSessionsForUser(userId);
        auditService.logSessionRevoked(admin, userId, null, auditContext(http, Map.of()));
        return ResponseEntity.ok(new StatusResponse("ok"));
    }
}


