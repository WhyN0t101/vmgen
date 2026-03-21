package io.vmgen.api.service;

import io.vmgen.api.config.JwtAuthenticationFilter;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.exceptions.ForbiddenException;
import io.vmgen.api.exceptions.ResourceNotFoundException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.user.User;
import io.vmgen.api.models.user.UserRole;
import io.vmgen.api.repository.UserRepository;
import io.vmgen.api.repository.vm.VmRequestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    static final int MAX_FLAG_FAILURES_PER_BUILD = 5;
    static final int EXHAUSTED_BUILDS_THRESHOLD = 3;
    static final Duration LOCKOUT_DURATION = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final VmRequestRepository vmRequestRepository;



    /**
     * Updates the role of a target user.
     * Prevents self-demotion and demotion of the last administrator account.
     * Revokes all active sessions for the target user after the role change.
     */
    @Transactional
    public void updateRole(User admin, UUID targetUserId, UserRole newRole, AuditContext ctx) {
        User target = userRepository.findById(targetUserId).orElseThrow(() ->
                new ValidationException("Validation failed", List.of("Target user not found")));

        if (admin.getId().equals(targetUserId) && newRole == UserRole.USER) {
            throw new ForbiddenException("Cannot demote your own administrator account");
        }

        if (target.getRole() == UserRole.ADMIN && newRole == UserRole.USER) {
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new ForbiddenException("Cannot demote the last administrator account");
            }
        }

        target.setRole(newRole);
        userRepository.save(target);

        sessionService.revokeAllSessionsForUser(targetUserId);
        auditService.logRoleUpdated(admin, targetUserId, newRole.name(), ctx);
    }

    /**
     * Resolves the authenticated admin's {@link User} entity from the security context.
     * Throws if the principal is null or the user cannot be found in the repository.
     */
    public User requireAdminUser(Authentication authentication) {
        JwtAuthenticationFilter.AuthPrincipal principal = (JwtAuthenticationFilter.AuthPrincipal) Objects.requireNonNull(
                authentication.getPrincipal(), "Authentication principal must not be null"
        );
        UUID adminId = UUID.fromString(principal.userId());
        return userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found: " + adminId));
    }

    /**
     * Locks the given user account until {@code now + duration}.
     * Callable from any service that enforces attempt limits (auth, flag submission).
     */
    @Transactional
    public void lockAccount(UUID userId, Duration duration) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setLockedUntil(Instant.now().plus(duration));
        userRepository.save(user);
        log.warn("Account locked for user {} until {}", userId, user.getLockedUntil());
    }

    /**
     * Records a failed flag submission attempt for the given build.
     * Triggers a {@link #LOCKOUT_DURATION} account lockout when the user has
     * {@link #EXHAUSTED_BUILDS_THRESHOLD} or more builds where failed attempts
     * reached {@link #MAX_FLAG_FAILURES_PER_BUILD}.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void recordFlagFailure(UUID buildId, UUID userId) {
        vmRequestRepository.incrementFlagFailures(buildId);
        long exhausted = vmRequestRepository.countExhaustedBuilds(userId, MAX_FLAG_FAILURES_PER_BUILD);
        if (exhausted >= EXHAUSTED_BUILDS_THRESHOLD) {
            lockAccount(userId, LOCKOUT_DURATION);
            log.warn("User {} locked after {} exhausted flag attempt builds", userId, exhausted);
        }
    }

    /**
     * Resolves the authenticated user's {@link User} entity from the security context.
     * Works for both USER and ADMIN roles.
     */
    public User requireUser(Authentication authentication) {
        JwtAuthenticationFilter.AuthPrincipal principal = (JwtAuthenticationFilter.AuthPrincipal) Objects.requireNonNull(
                authentication.getPrincipal(), "Authentication principal must not be null"
        );
        UUID userId = UUID.fromString(principal.userId());
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

}


