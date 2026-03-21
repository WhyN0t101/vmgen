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

    // Auth events
    private static final String ACTION_LOGIN = "login";
    private static final String ACTION_LOGIN_FAILED = "login_failed";
    private static final String ACTION_SESSION_REVOKED = "session_revoked";
    private static final String ACTION_ROLE_UPDATED = "role_updated";
    private static final String ACTION_LOGOUT = "logout";

    // Build lifecycle events
    private static final String ACTION_BUILD_CREATED = "build_created";
    private static final String ACTION_BUILD_DOWNLOADED = "build_downloaded";
    private static final String ACTION_BUILD_PROVENANCE_RETRIEVED = "build_provenance_retrieved";

    // Worker events
    private static final String ACTION_WORKER_JOB_COMPLETED = "worker_job_completed";
    private static final String ACTION_WORKER_JOB_FAILED = "worker_job_failed";

    // Flag events (used by FlagService)
    public static final String ACTION_FLAG_SUBMITTED = "flag_submitted";
    public static final String ACTION_FLAG_REJECTED = "flag_rejected";
    public static final String ACTION_ACCOUNT_LOCKED = "account_locked";

    // Template admin events
    private static final String ACTION_TEMPLATE_REGISTERED = "template_registered";
    private static final String ACTION_TEMPLATE_VALIDATED = "template_validated";
    private static final String ACTION_TEMPLATE_DEPRECATED = "template_deprecated";
    private static final String ACTION_TEMPLATE_METADATA_UPDATED = "template_metadata_updated";

    // Script admin events
    private static final String ACTION_SCRIPT_CREATED = "script_created";
    private static final String ACTION_SCRIPT_UPDATED = "script_updated";
    private static final String ACTION_SCRIPT_DEPRECATED = "script_deprecated";



    //  Auth events 

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

    //  Build lifecycle 

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBuildCreated(User user, UUID buildId, UUID templateId, AuditContext ctx) {
        write(user, ACTION_BUILD_CREATED, "build", buildId, ctx,
                Map.of("buildId", buildId.toString(), "templateId", templateId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logBuildDownloaded(User user, UUID buildId, AuditContext ctx) {
        write(user, ACTION_BUILD_DOWNLOADED, "build", buildId, ctx,
                Map.of("buildId", buildId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logProvenanceRetrieved(User user, UUID buildId, AuditContext ctx) {
        write(user, ACTION_BUILD_PROVENANCE_RETRIEVED, "build", buildId, ctx,
                Map.of("buildId", buildId.toString()));
    }

    //  Worker events 

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWorkerJobCompleted(String workerId, UUID buildId) {
        write(null, ACTION_WORKER_JOB_COMPLETED, "build", buildId, null,
                Map.of("workerId", workerId, "buildId", buildId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logWorkerJobFailed(String workerId, UUID buildId, String errorType) {
        write(null, ACTION_WORKER_JOB_FAILED, "build", buildId, null,
                Map.of("workerId", workerId, "buildId", buildId.toString(), "errorType", errorType));
    }

    //  Flag events 

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFlagSubmitted(User user, UUID buildId, UUID scriptId, boolean valid, AuditContext ctx) {
        write(user, valid ? ACTION_FLAG_SUBMITTED : ACTION_FLAG_REJECTED, "build", buildId, ctx,
                Map.of("buildId", buildId.toString(), "scriptId", scriptId.toString(), "valid", valid));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccountLocked(User user, UUID buildId, AuditContext ctx) {
        write(user, ACTION_ACCOUNT_LOCKED, "user", user.getId(), ctx,
                Map.of("buildId", buildId.toString(), "userId", user.getId().toString()));
    }

    //  Template admin events 

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTemplateRegistered(User admin, UUID templateId, String identifier, AuditContext ctx) {
        write(admin, ACTION_TEMPLATE_REGISTERED, "template", templateId, ctx,
                Map.of("templateId", templateId.toString(), "identifier", identifier));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTemplateValidated(User admin, UUID templateId, AuditContext ctx) {
        write(admin, ACTION_TEMPLATE_VALIDATED, "template", templateId, ctx,
                Map.of("templateId", templateId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTemplateDeprecated(User admin, UUID templateId, AuditContext ctx) {
        write(admin, ACTION_TEMPLATE_DEPRECATED, "template", templateId, ctx,
                Map.of("templateId", templateId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTemplateMetadataUpdated(User admin, UUID templateId, AuditContext ctx) {
        write(admin, ACTION_TEMPLATE_METADATA_UPDATED, "template", templateId, ctx,
                Map.of("templateId", templateId.toString()));
    }

    //  Script admin events 

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logScriptCreated(User admin, UUID scriptId, String identifier, AuditContext ctx) {
        write(admin, ACTION_SCRIPT_CREATED, "script", scriptId, ctx,
                Map.of("scriptId", scriptId.toString(), "identifier", identifier));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logScriptUpdated(User admin, UUID scriptId, AuditContext ctx) {
        write(admin, ACTION_SCRIPT_UPDATED, "script", scriptId, ctx,
                Map.of("scriptId", scriptId.toString()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logScriptDeprecated(User admin, UUID scriptId, AuditContext ctx) {
        write(admin, ACTION_SCRIPT_DEPRECATED, "script", scriptId, ctx,
                Map.of("scriptId", scriptId.toString()));
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
            AuditLog auditLog = new AuditLog();
            auditLog.setUser(user);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setIpAddress(ctx != null ? ctx.ipAddress() : null);
            auditLog.setUserAgent(ctx != null ? ctx.userAgent() : null);

            Map<String, Object> merged = extraMetadata;
            if (ctx != null && ctx.metadata() != null && !ctx.metadata().isEmpty()) {
                merged = new HashMap<>(extraMetadata);
                // ctx.metadata() takes priority over extraMetadata for same-keyed entries.
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
            log.warn("Metadata serialization failed (keys: {}): {}", obj.keySet(), e.getMessage());
            return "{\"error\":\"metadata_serialization_failed\"}";
        }
    }
}