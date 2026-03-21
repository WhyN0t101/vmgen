package io.vmgen.api.controller;

import io.vmgen.api.config.JwtAuthenticationFilter.AuthPrincipal;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.auth.AuthResponse;
import io.vmgen.api.dto.auth.LoginRequest;
import io.vmgen.api.dto.auth.RegisterRequest;
import io.vmgen.api.dto.common.StatusResponse;
import io.vmgen.api.service.AuthService;
import io.vmgen.api.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;

import static io.vmgen.api.utils.HttpUtils.auditContext;
import static io.vmgen.api.utils.HttpUtils.getClientIp;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;


    @PostMapping("/register")
    public ResponseEntity<StatusResponse> register(@Valid @RequestBody RegisterRequest req,
                                                    HttpServletRequest http) {
        rateLimitService.checkRegisterRateLimit(getClientIp(http));
        authService.register(req);
        return ResponseEntity.ok(new StatusResponse("ok"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        String clientIp = getClientIp(http);
        rateLimitService.checkLoginRateLimit(clientIp);
        
        AuditContext ctx = auditContext(http, Map.of());
        return ResponseEntity.ok(authService.login(req, ctx));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    public ResponseEntity<StatusResponse> logout(Authentication authentication, HttpServletRequest http) {
        AuthPrincipal principal = (AuthPrincipal) Objects.requireNonNull(
                authentication.getPrincipal(), "Principal must not be null"
        );        UUID sessionId = UUID.fromString(principal.sessionId());
        UUID userId = UUID.fromString(principal.userId());
        authService.logout(sessionId, userId, auditContext(http, Map.of()));
        return ResponseEntity.ok(new StatusResponse("ok"));
    }




}


