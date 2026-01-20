package io.vmgen.api.service;

import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.exceptions.ForbiddenException;
import io.vmgen.api.exceptions.ValidationException;
import io.vmgen.api.models.User;
import io.vmgen.api.models.UserRole;
import io.vmgen.api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
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

    @Transactional
    public void updateRole(User admin, UUID targetUserId, UserRole newRole, AuditContext ctx) {
        User target = userRepository.findById(targetUserId).orElseThrow(() ->
                new ValidationException("Validation failed", List.of("Target user not found")));

        // Prevent self-demotion
        if (admin.getId().equals(targetUserId) && newRole == UserRole.user) {
            throw new ForbiddenException("Cannot demote your own administrator account");
        }

        // Prevent demoting the last admin
        // Check adminCount <= 1 (not == 1) to be defensive against race conditions
        if (target.getRole() == UserRole.admin && newRole == UserRole.user) {
            long adminCount = userRepository.countByRole(UserRole.admin);
            if (adminCount <= 1) {
                throw new ForbiddenException("Cannot demote the last administrator account");
            }
        }

        target.setRole(newRole);
        userRepository.save(target);

        // Mandatory: revoke all sessions so old role claims cannot persist.
        sessionService.revokeAllSessionsForUser(targetUserId);

        auditService.logRoleUpdated(admin, targetUserId, newRole.name(), ctx);
    }
}


