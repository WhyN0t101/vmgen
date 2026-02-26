package io.vmgen.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.audit.AuditLog;
import io.vmgen.api.models.user.User;
import io.vmgen.api.repository.AuditLogRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private static final String ACTION_LOGIN = "login";
    private static final String ACTION_LOGIN_FAILED = "login_failed";
    private static final String ACTION_SESSION_REVOKED = "session_revoked";
    private static final String ACTION_ROLE_UPDATED = "role_updated";
    private static final String ACTION_LOGOUT = "logout";


    /**
     * Records a successful login event in a new independent transaction.
     * Persists regardless of the calling transaction outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(User user, UUID sessionId, AuditContext ctx) {
        write(user, ACTION_LOGIN, "session", sessionId, ctx,
                Map.of("sessionId", sessionId.toString()));
    }

    /**
     * Records a failed login attempt against the provided identifier.
     * User is null since identity cannot be confirmed on failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginFailed(String identifier, AuditContext ctx) {
        write(null, ACTION_LOGIN_FAILED, null, null, ctx,
                Map.of("identifier", identifier));
    }

    /**
     * Records a session revocation event performed by an admin.
     * sessionId may be null when all sessions for a user are revoked in bulk.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSessionRevoked(User admin, UUID targetUserId, UUID sessionId, AuditContext ctx) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", targetUserId.toString());
        if (sessionId != null) {
            metadata.put("sessionId", sessionId.toString());
        }
        write(admin, ACTION_SESSION_REVOKED, "session", sessionId, ctx, metadata);
    }

    /**
     * Records a logout event for the given user and session.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(User user, UUID sessionId, AuditContext ctx) {
        write(user, ACTION_LOGOUT, "session", sessionId, ctx,
                Map.of("sessionId", sessionId.toString()));
    }

    /**
     * Records a role change performed by an admin on a target user.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRoleUpdated(User admin, UUID targetUserId, String newRole, AuditContext ctx) {
        write(admin, ACTION_ROLE_UPDATED, "user", targetUserId, ctx, Map.of(
                "targetUserId", targetUserId.toString(),
                "newRole", newRole
        ));
    }

    /**
     * Core write method for all audit events.
     * Merges extra metadata with context metadata before persisting.
     * Catches and logs all exceptions to ensure audit failures never propagate to the caller.
     */
    private void write(
            User user,
            String action,
            String resourceType,
            UUID resourceId,
            AuditContext ctx,
            Map<String, Object> extraMetadata
    ) {
        try {
            AuditLog auditLog = new AuditLog();  // Changed from 'log' to 'auditLog'
            auditLog.setUser(user);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setIpAddress(ctx != null ? ctx.ipAddress() : null);
            auditLog.setUserAgent(ctx != null ? ctx.userAgent() : null);

            Map<String, Object> merged = extraMetadata;
            if (ctx != null && ctx.metadata() != null && !ctx.metadata().isEmpty()) {
                merged = new HashMap<>(extraMetadata);
                merged.putAll(ctx.metadata());
            }
            auditLog.setMetadataJson(toJsonSafe(merged));

            auditLogRepository.save(auditLog);

            log.debug("Audit log created: action={}, user={}, resource={}:{}",
                    action,
                    user != null ? user.getUsername() : "anonymous",
                    resourceType,
                    resourceId);

        } catch (Exception e) {
            log.error("Failed to write audit log: action={}, user={}, error={}",
                    action,
                    user != null ? user.getId() : null,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Serialises a metadata map to JSON.
     * Returns a fallback error JSON string if serialisation fails rather than throwing.
     */
    private String toJsonSafe(Map<String, Object> obj) {
        if (obj == null || obj.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Metadata serialization failed: {}", e.getMessage());
            return "{\"error\":\"metadata_serialization_failed\"}";
        }
    }
}