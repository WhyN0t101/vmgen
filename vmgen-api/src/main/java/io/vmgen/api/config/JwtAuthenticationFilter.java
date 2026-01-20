package io.vmgen.api.config;

import io.vmgen.api.dto.auth.JwtClaimsDto;
import io.vmgen.api.exceptions.SessionRevokedException;
import io.vmgen.api.models.Session;
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
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final SessionService sessionService;

    public JwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
        this.jwtService = jwtService;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring("Bearer ".length()).trim();
        JwtClaimsDto claims = jwtService.parseAndValidate(rawToken);

        UUID sessionId = UUID.fromString(claims.sessionId());
        Instant now = Instant.now();
        Session session = sessionService.getActiveSession(sessionId, now);
        if (session == null) {
            throw new SessionRevokedException();
        }

        String computedHash = TokenHashUtil.sha256HexLower(rawToken);
        if (!TokenHashUtil.constantTimeEquals(session.getTokenHash(), computedHash)) {
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
    }

    public record AuthPrincipal(String userId, String role, String sessionId) {}
}


