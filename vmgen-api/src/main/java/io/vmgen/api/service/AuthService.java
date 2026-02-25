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
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final io.vmgen.api.config.JwtProperties jwtProperties;



    /**
     * Registers a new user after validating username/email uniqueness and password policy.
     * Email is normalised to lowercase before persisting.
     */
    @Transactional
    public void register(RegisterRequest req) {
        validateRegistration(req);

        PasswordPolicyValidator.validate(req.password());

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(UserRole.USER);
        user.setEmailVerifiedAt(null);

        userRepository.save(user);

        log.info("User registered: {}", user.getUsername());
    }

    /**
     * Validates that the username and email are not already in use.
     * Throws {@link ValidationException} on conflict.
     */
    private void validateRegistration(RegisterRequest req) {
        if (userRepository.findByUsernameIgnoreCase(req.username()).isPresent()) {
            throw new ValidationException("Validation failed", List.of("Username already in use"));
        }
        if (userRepository.findByEmailIgnoreCase(req.email()).isPresent()) {
            throw new ValidationException("Validation failed", List.of("Email already in use"));
        }
    }

    /**
     * Authenticates a user by email or username.
     * Uses constant-time password verification to prevent timing attacks.
     * Revokes all existing sessions before issuing a new one.
     * Throws {@link AuthenticationFailedException} on invalid credentials or session creation failure.
     */
    @Transactional
    public AuthResponse login(LoginRequest req, AuditContext ctx) {
        String identifier = req.identifier();

        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElse(null);

        // Constant-time password verification to prevent timing attacks
        String passwordHash = user != null ? user.getPasswordHash() : "$2a$10$dummyHashToPreventTimingAttack";
        boolean passwordMatches = passwordEncoder.matches(req.password(), passwordHash);

        if (user == null || !passwordMatches) {
            auditService.logLoginFailed(identifier, ctx);
            throw new AuthenticationFailedException();
        }

        // TODO: Check if user is locked/disabled/email unverified
        // if (user.isLocked()) throw new AccountLockedException();
        // if (!user.isEmailVerified()) throw new EmailNotVerifiedException();

        // Only revoke sessions after successful authentication
        sessionService.revokeAllSessionsForUser(user.getId());

        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        long ttlSeconds = user.getRole() == UserRole.ADMIN
                ? jwtProperties.getAdminTtlSeconds()
                : jwtProperties.getUserTtlSeconds();
        Instant exp = now.plusSeconds(ttlSeconds);

        String token = jwtService.generateToken(user.getId(), user.getRole().name(), sessionId, now, exp);
        String tokenHash = TokenHashUtil.sha256HexLower(token);

        try {
            sessionService.createSession(user, sessionId, tokenHash, exp);
        } catch (Exception e) {
            log.error("Session creation failed for user {}: {}", user.getUsername(), e.getMessage());
            auditService.logLoginFailed(identifier, enrichContext(ctx));
            throw new AuthenticationFailedException();
        }

        auditService.logLogin(user, sessionId, ctx);

        log.info("User logged in: {} (session: {})", user.getUsername(), sessionId);

        return new AuthResponse(token, exp.toString(), user.getRole().name());
    }

    /**
     * Enriches an {@link AuditContext} with a failure reason metadata entry.
     * Used when session creation fails after successful authentication.
     */
    private AuditContext enrichContext(AuditContext ctx) {
        return new AuditContext(
                ctx != null ? ctx.ipAddress() : null,
                ctx != null ? ctx.userAgent() : null,
                Map.of("reason", "session_create_failed")        );
    }

    /**
     * Logs out the user by revoking the session and recording an audit event.
     * No-ops silently if the user cannot be found, as the session is revoked regardless.
     */
    public void logout(UUID sessionId, UUID userId, AuditContext ctx) {
        sessionService.logout(sessionId, userId);
        userRepository.findById(userId).ifPresent(user ->
                auditService.logLogout(user, sessionId, ctx));
    }
}