package io.vmgen.api.config;

import io.vmgen.api.utils.TokenHashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class WorkerApiKeyFilter extends OncePerRequestFilter {

    @Value("${worker.api.key}")
    private String workerApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        // Only check /api/worker/** endpoints
        if (!request.getRequestURI().startsWith("/api/worker")) {
            chain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader("X-Worker-Api-Key");

        // Constant-time comparison to prevent timing attacks
        if (providedKey == null || !TokenHashUtil.constantTimeEquals(workerApiKey, providedKey)) {
            log.warn("Unauthorized worker API request from IP: {} to: {}",
                    getClientIp(request), request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"Invalid worker API key\"}");
            return;
        }

        log.debug("Worker API authenticated for: {}", request.getRequestURI());
        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}