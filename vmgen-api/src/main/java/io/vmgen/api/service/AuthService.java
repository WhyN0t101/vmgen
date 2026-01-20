package io.vmgen.api.service;

import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.auth.AuthResponse;
import io.vmgen.api.dto.auth.LoginRequest;
import io.vmgen.api.dto.auth.RegisterRequest;
import io.vmgen.api.exceptions.AuthenticationFailedException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.User;
import io.vmgen.api.models.UserRole;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.utils.PasswordPolicyValidator;
import io.vmgen.api.utils.TokenHashUtil;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final io.vmgen.api.config.JwtProperties jwtProperties;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SessionService sessionService,
            AuditService auditService,
            io.vmgen.api.config.JwtProperties jwtProperties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public void register(RegisterRequest req) {
        // Validate uniqueness (case-insensitive)
        if (userRepository.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new ValidationException("Validation failed", List.of("Username already in use"));
        }
        if (userRepository.findByEmailIgnoreCase(req.email()).isPresent()) {
            throw new ValidationException("Validation failed", List.of("Email already in use"));
        }

        PasswordPolicyValidator.validate(req.password());

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(UserRole.user);
        user.setEmailVerifiedAt(null);

        userRepository.save(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, AuditContext ctx) {
        // Accept username or email
        String identifier = req.identifier();
        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElse(null);

        if (user == null) {
            auditService.logLoginFailed(identifier, ctx);
            throw new AuthenticationFailedException();
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            auditService.logLoginFailed(identifier, ctx);
            throw new AuthenticationFailedException();
        }

        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        long ttlSeconds = user.getRole() == UserRole.admin
                ? jwtProperties.getAdminTtlSeconds()
                : jwtProperties.getUserTtlSeconds();
        Instant exp = now.plusSeconds(ttlSeconds);

        String token = jwtService.generateToken(user.getId(), user.getRole().name(), sessionId, now, exp);
        String tokenHash = TokenHashUtil.sha256HexLower(token);

        try {
            sessionService.createSession(user, sessionId, tokenHash, exp);
        } catch (RuntimeException e) {
            // Password verified but session creation failed; audit it as login_failed with metadata.
            auditService.logLoginFailed(identifier, new AuditContext(
                    ctx != null ? ctx.ipAddress() : null,
                    ctx != null ? ctx.userAgent() : null,
                    java.util.Map.of("reason", "session_create_failed")
            ));
            throw e;
        }

        auditService.logLogin(user, sessionId, ctx);

        return new AuthResponse(token, exp.toString(), user.getRole().name());
    }
}


