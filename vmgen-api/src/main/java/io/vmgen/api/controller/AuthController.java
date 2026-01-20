package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.auth.AuthResponse;
import io.vmgen.api.dto.auth.LoginRequest;
import io.vmgen.api.dto.auth.RegisterRequest;
import io.vmgen.api.service.AuthService;
import io.vmgen.api.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        AuditContext ctx = auditContext(http, Map.of());
        return ResponseEntity.ok(authService.login(req, ctx));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication, HttpServletRequest http) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.ok(Map.of("status", "ok"));
        }
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        UUID sessionId = UUID.fromString(principal.sessionId());
        UUID userId = UUID.fromString(principal.userId());
        sessionService.logout(sessionId, userId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private static AuditContext auditContext(HttpServletRequest req, Map<String, Object> metadata) {
        String ip = req.getRemoteAddr();
        String ua = req.getHeader("User-Agent");
        return new AuditContext(ip, ua, metadata);
    }
}


