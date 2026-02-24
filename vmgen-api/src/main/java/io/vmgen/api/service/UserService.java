package io.vmgen.api.service;

import io.vmgen.api.config.JwtAuthenticationFilter;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.exceptions.ForbiddenException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.User;
import io.vmgen.api.models.UserRole;
import io.vmgen.api.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final SessionService sessionService;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, SessionService sessionService, AuditService auditService) {
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.auditService = auditService;
    }

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

}


