package io.vmgen.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.AuditLog;
import io.vmgen.api.models.User;
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
    // Audit action constants
    private static final String ACTION_LOGIN = "login";
    private static final String ACTION_LOGIN_FAILED = "login_failed";
    private static final String ACTION_SESSION_REVOKED = "session_revoked";
    private static final String ACTION_ROLE_UPDATED = "role_updated";
    private static final String ACTION_LOGOUT = "logout";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(User user, UUID sessionId, AuditContext ctx) {
        write(user, ACTION_LOGIN, "session", sessionId, ctx,
                Map.of("sessionId", sessionId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginFailed(String identifier, AuditContext ctx) {
        write(null, ACTION_LOGIN_FAILED, null, null, ctx,
                Map.of("identifier", identifier));
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSessionRevoked(User admin, UUID targetUserId, UUID sessionId, AuditContext ctx) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetUserId", targetUserId.toString());
        if (sessionId != null) {
            metadata.put("sessionId", sessionId.toString());
        }
        write(admin, ACTION_SESSION_REVOKED, "session", sessionId, ctx, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(User user, UUID sessionId, AuditContext ctx) {
        write(user, ACTION_LOGOUT, "session", sessionId, ctx,
                Map.of("sessionId", sessionId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRoleUpdated(User admin, UUID targetUserId, String newRole, AuditContext ctx) {
        write(admin, ACTION_ROLE_UPDATED, "user", targetUserId, ctx, Map.of(
                "targetUserId", targetUserId.toString(),
                "newRole", newRole
        ));
    }

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