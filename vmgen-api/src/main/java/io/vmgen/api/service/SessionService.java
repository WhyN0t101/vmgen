package io.vmgen.api.service;

import io.vmgen.api.dto.admin.ActiveSessionDto;
import io.vmgen.api.exceptions.SessionExpiredException;
import io.vmgen.api.models.Session;
import io.vmgen.api.models.User;
import io.vmgen.api.repository.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {
    private static final Duration ACTIVITY_UPDATE_THRESHOLD = Duration.ofSeconds(60);

    private final SessionRepository sessionRepository;

    @Transactional
    public Session createSession(User user, UUID sessionId, String tokenHash, Instant expiresAt) {
        if (user == null || sessionId == null || tokenHash == null || expiresAt == null) {
            throw new IllegalArgumentException("All session parameters are required");
        }

        if (sessionRepository.existsById(sessionId)) {
            log.warn("Session ID collision detected: {}", sessionId);
            throw new IllegalStateException("Session ID already exists");
        }

        Session session = new Session();
        session.setId(sessionId);
        session.setUser(user);
        session.setTokenHash(tokenHash);
        session.setExpiresAt(expiresAt);

        session = sessionRepository.save(session);

        log.info("Session created for user {} (session: {}, expires: {})",
                user.getUsername(), sessionId, expiresAt);

        return session;
    }

    @Transactional
    public void logout(UUID sessionId, UUID userId) {
        int deleted = sessionRepository.deleteByIdAndUserId(sessionId, userId);
        if (deleted > 0) {
            log.info("User {} logged out (session: {})", userId, sessionId);
        }

    }

    @Transactional
    public void revokeSession(UUID sessionId, UUID adminUserId) {
        Optional<Session> session = sessionRepository.findById(sessionId);
        if (session.isPresent()) {
            sessionRepository.deleteById(sessionId);
            log.warn("Session {} revoked by admin {}", sessionId, adminUserId);
        }
    }

    @Transactional
    public void revokeAllSessionsForUser(UUID userId) {
        int deleted = sessionRepository.deleteAllByUserId(userId);
        if (deleted > 0) {
            log.info("Revoked {} sessions for user {}", deleted, userId);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Session> getActiveSession(UUID sessionId, Instant now) {
        Session session = sessionRepository.findById(sessionId).orElse(null);

        if (session == null) {
            return Optional.empty();
        }

        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            log.debug("Session {} is expired", sessionId);
            throw new SessionExpiredException();
        }

        return Optional.of(session);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getSessionOwnerUserId(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .map(s -> s.getUser().getId());
    }

    /**
     * Called from JwtAuthenticationFilter (non-transactional context).
     * Uses isolated transaction to avoid coupling to request handlers.
     * Failures are logged but don't block authentication.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public void updateActivityIfStale(UUID sessionId, Instant now) {
        try {
            Session session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                log.debug("Cannot update activity for non-existent session: {}", sessionId);
                return;
            }

            Instant last = session.getLastActivityAt();
            boolean shouldUpdate = last == null ||
                    Duration.between(last, now).compareTo(ACTIVITY_UPDATE_THRESHOLD) > 0;

            if (shouldUpdate) {
                session.setLastActivityAt(now);
                sessionRepository.save(session);
                log.trace("Updated activity for session {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to update session activity for {}: {}", sessionId, e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ActiveSessionDto> listActiveSessions() {
        Instant now = Instant.now();
        return sessionRepository.findAllActiveWithUser(now).stream()
                .map(s -> new ActiveSessionDto(
                        s.getId().toString(),
                        s.getUser().getId().toString(),
                        s.getUser().getUsername(),
                        s.getUser().getRole().name(),
                        s.getCreatedAt().toString(),
                        s.getLastActivityAt() != null ? s.getLastActivityAt().toString() : null,
                        s.getExpiresAt().toString()
                ))
                .toList();
    }

    @Transactional
    public void cleanupExpiredAndIdle(Duration idleTimeout) {
        Instant now = Instant.now();

        int expiredCount = sessionRepository.deleteExpired(now);
        log.info("Cleaned up {} expired sessions", expiredCount);

        if (idleTimeout != null) {
            Instant cutoff = now.minus(idleTimeout);
            int idleCount = sessionRepository.deleteIdle(cutoff);
            log.info("Cleaned up {} idle sessions (timeout: {})", idleCount, idleTimeout);
        }
    }
}