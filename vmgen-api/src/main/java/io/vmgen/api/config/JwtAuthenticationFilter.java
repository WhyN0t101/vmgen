package io.vmgen.api.config;

import io.vmgen.api.dto.auth.JwtClaimsDto;
import io.vmgen.api.exceptions.InvalidTokenException;
import io.vmgen.api.exceptions.SessionExpiredException;
import io.vmgen.api.exceptions.SessionRevokedException;
import io.vmgen.api.models.user.Session;
import io.vmgen.api.service.JwtService;
import io.vmgen.api.service.SessionService;
import io.vmgen.api.utils.TokenHashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
/*
 * Authenticates incoming HTTP requests using a Bearer JWT.
 * Validates the token, checks the backing session record, and populates
 * the Spring Security context with user identity and role authorities.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String rawToken = authHeader.substring("Bearer ".length()).trim();
            JwtClaimsDto claims = jwtService.parseAndValidate(rawToken);

            UUID sessionId = UUID.fromString(claims.sessionId());
            Instant now = Instant.now();
            Optional<Session> session = sessionService.getActiveSession(sessionId, now);
            if (session.isEmpty()) {
                throw new SessionRevokedException();
            }

            String computedHash = TokenHashUtil.sha256HexLower(rawToken);
            if (!TokenHashUtil.constantTimeEquals(session.get().getTokenHash(), computedHash)) {
                throw new SessionRevokedException();
            }

            // Throttled activity tracking (isolated transaction inside service)
            sessionService.updateActivityIfStale(sessionId, now);

            String role = claims.role();
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    new AuthPrincipal(claims.userId(), role, claims.sessionId()),
                    null,
                    authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (SessionRevokedException e) {
            log.debug("Session revoked: {}", e.getMessage());
            sendErrorResponse(response, "Session has been revoked");
        } catch (SessionExpiredException e) {
            log.debug("Session expired: {}", e.getMessage());
            sendErrorResponse(response, "Session expired");
        } catch (InvalidTokenException e) {
            log.warn("Invalid token: {}", e.getMessage());
            sendErrorResponse(response, "Invalid token");
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage(), e);
            sendErrorResponse(response, "Authentication failed");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format("{\"message\":\"%s\"}", message));
    }

    public record AuthPrincipal(String userId, String role, String sessionId) {}
}