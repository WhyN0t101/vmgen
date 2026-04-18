package io.vmgen.api.service;

import io.vmgen.api.config.JwtProperties;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.dto.auth.AuthResponse;
import io.vmgen.api.dto.auth.LoginRequest;
import io.vmgen.api.dto.auth.RegisterRequest;
import io.vmgen.api.exceptions.AccountLockedException;
import io.vmgen.api.exceptions.AuthenticationFailedException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.user.UserRole;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.utils.PasswordPolicyValidator;
import io.vmgen.api.utils.TokenHashUtil;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
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
    private final JwtProperties jwtProperties;

    // Pre-computed using the live encoder so timing matches a real verification.
    // Prevents response-time enumeration of whether an account exists.
    private String dummyPasswordHash;

    @PostConstruct
    void initDummyHash() {
        dummyPasswordHash = passwordEncoder.encode("vmgen-dummy-password-for-timing");
    }



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
        boolean usernameTaken = userRepository.findByUsernameIgnoreCase(req.username()).isPresent();
        boolean emailTaken    = userRepository.findByEmailIgnoreCase(req.email()).isPresent();
        if (usernameTaken || emailTaken) {
            // Generic message prevents revealing which field is already registered.
            throw new ValidationException("Validation failed",
                    List.of("An account with this username or email is already registered"));
        }
    }

    /**
     * Authenticates a user by email or username.
     * Uses constant-time password verification to prevent timing attacks.
     * Lockout status is only surfaced after credentials are verified to prevent account enumeration.
     * Revokes all existing sessions before issuing a new one.
     * Throws {@link AuthenticationFailedException} on invalid credentials or session creation failure.
     * Throws {@link AccountLockedException} when the account is currently locked (e.g. due to flag failures).
     */
    @Transactional
    public AuthResponse login(LoginRequest req, AuditContext ctx) {
        String identifier = req.identifier();

        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsernameIgnoreCase(identifier))
                .orElse(null);

        // Constant-time password verification to prevent timing attacks
        String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(req.password(), passwordHash);

        if (user == null || !passwordMatches) {
            auditService.logLoginFailed(identifier, ctx);
            throw new AuthenticationFailedException();
        }

        // Lockout check is performed after credential verification.
        // Revealing lockout status only to callers who know the correct password
        // prevents account enumeration via differential responses.
        Instant now = Instant.now();
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
            long retryAfterSeconds = Duration.between(now, user.getLockedUntil()).getSeconds();
            auditService.logLoginFailed(identifier, ctx);
            throw new AccountLockedException(retryAfterSeconds);
        }

        // Only revoke sessions after successful authentication
        sessionService.revokeAllSessionsForUser(user.getId());

        UUID sessionId = UUID.randomUUID();
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

        return new AuthResponse(token, exp.toString(), user.getRole().name(), user.getId());
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