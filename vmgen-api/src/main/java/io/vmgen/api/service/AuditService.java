package io.vmgen.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.models.AuditLog;
import io.vmgen.api.models.User;
import io.vmgen.api.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(User user, UUID sessionId, AuditContext ctx) {
        write(user, "login", "session", sessionId, ctx, Map.of("sessionId", sessionId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginFailed(String identifier, AuditContext ctx) {
        write(null, "login_failed", null, null, ctx, Map.of("identifier", identifier));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(UUID userId, UUID sessionId, AuditContext ctx, User user) {
        write(user, "logout", "session", sessionId, ctx, Map.of("sessionId", sessionId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSessionRevoked(User admin, UUID targetUserId, UUID sessionId, AuditContext ctx) {
        Map<String, Object> md = new java.util.HashMap<>();
        md.put("targetUserId", targetUserId.toString());
        if (sessionId != null) md.put("sessionId", sessionId.toString());
        write(admin, "session_revoked", "session", sessionId, ctx, md);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRoleUpdated(User admin, UUID targetUserId, String newRole, AuditContext ctx) {
        write(admin, "role_updated", "user", targetUserId, ctx, Map.of(
                "targetUserId", targetUserId.toString(),
                "newRole", newRole
        ));
    }

    private void write(User user, String action, String resourceType, UUID resourceId, AuditContext ctx, Map<String, Object> extraMetadata) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setIpAddress(ctx != null ? ctx.ipAddress() : null);
        log.setUserAgent(ctx != null ? ctx.userAgent() : null);

        Map<String, Object> merged = extraMetadata;
        if (ctx != null && ctx.metadata() != null && !ctx.metadata().isEmpty()) {
            merged = new java.util.HashMap<>(extraMetadata);
            merged.putAll(ctx.metadata());
        }
        log.setMetadataJson(toJsonSafe(merged));
        auditLogRepository.save(log);
    }

    private String toJsonSafe(Map<String, Object> obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            // Avoid breaking core auth flows if metadata serialization fails.
            return "{\"error\":\"metadata_serialization_failed\"}";
        }
    }
}


