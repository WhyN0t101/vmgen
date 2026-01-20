package io.vmgen.api.service;

import io.vmgen.api.dto.admin.ActiveSessionDto;
import io.vmgen.api.dto.audit.AuditContext;
import io.vmgen.api.exceptions.SessionExpiredException;
import io.vmgen.api.models.Session;
import io.vmgen.api.models.User;
import io.vmgen.api.repository.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private static final Duration ACTIVITY_UPDATE_THRESHOLD = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final AuditService auditService;

    public SessionService(SessionRepository sessionRepository, AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Session createSession(User user, UUID sessionId, String tokenHash, Instant expiresAt) {
        Session session = new Session();
        session.setId(sessionId);
        session.setUser(user);
        session.setTokenHash(tokenHash);
        session.setExpiresAt(expiresAt);
        return sessionRepository.save(session);
    }

    @Transactional
    public void logout(UUID sessionId, UUID userId) {
        sessionRepository.deleteByIdAndUserId(sessionId, userId);
    }

    @Transactional
    public void revokeSession(UUID sessionId) {
        sessionRepository.deleteById(sessionId);
    }

    @Transactional
    public void revokeAllSessionsForUser(UUID userId) {
        sessionRepository.deleteAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Session getActiveSession(UUID sessionId, Instant now) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) return null;
        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            throw new SessionExpiredException();
        }
        return session;
    }

    @Transactional(readOnly = true)
    public UUID getSessionOwnerUserId(UUID sessionId) {
        return sessionRepository.findById(sessionId).map(s -> s.getUser().getId()).orElse(null);
    }

    /**
     * Called from JwtAuthenticationFilter (non-transactional context).
     * Uses an isolated, short transaction to avoid coupling to request handlers.
     * Errors are logged but not propagated to avoid blocking authentication.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void updateActivityIfStale(UUID sessionId, Instant now) {
        try {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) return;
            Instant last = session.getLastActivityAt();
            if (last == null || Duration.between(last, now).compareTo(ACTIVITY_UPDATE_THRESHOLD) > 0) {
                session.setLastActivityAt(now);
                sessionRepository.save(session);
            }
        } catch (Exception e) {
            // Log but don't propagate - activity update failures shouldn't block requests
            log.warn("Failed to update session activity for {}: {}", sessionId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionDto> listActiveSessions() {
        Instant now = Instant.now();
        return sessionRepository.findAllActiveWithUser(now).stream().map(s -> new ActiveSessionDto(
                s.getId().toString(),
                s.getUser().getId().toString(),
                s.getUser().getUsername(),
                s.getUser().getRole().name(),
                s.getCreatedAt().toString(),
                s.getLastActivityAt().toString(),
                s.getExpiresAt().toString()
        )).toList();
    }

    @Transactional
    public void cleanupExpiredAndIdle(Duration idleTimeout) {
        Instant now = Instant.now();
        sessionRepository.deleteExpired(now);
        if (idleTimeout != null) {
            Instant cutoff = now.minus(idleTimeout);
            sessionRepository.deleteIdle(cutoff);
        }
    }

    @Transactional
    public void auditSessionRevoked(User admin, UUID targetUserId, UUID sessionId, AuditContext ctx) {
        auditService.logSessionRevoked(admin, targetUserId, sessionId, ctx);
    }
}


