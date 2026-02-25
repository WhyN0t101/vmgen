package io.vmgen.api.config;

import io.vmgen.api.utils.TokenHashUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import static io.vmgen.api.utils.HttpUtils.getClientIp;

@Component
@Slf4j
/*
 * Filter that enforces an API key for all {@code /api/worker/**} requests.
 * Provides a simple machine-to-machine authentication mechanism independent
 * of user JWT-based authentication.
 */
public class WorkerApiKeyFilter extends OncePerRequestFilter {

    @Value("${worker.api.key}")
    private String workerApiKey;

    /**
     * Ensures that a worker API key is configured before the application
     * starts accepting requests.
     */
    @PostConstruct
    public void validate() {
        if (workerApiKey == null || workerApiKey.isBlank()) {
            throw new IllegalStateException("worker.api.key must be configured");
        }
    }

    /**
     * Validates the {@code X-Worker-Api-Key} header for worker endpoints and
     * rejects requests with missing or invalid keys using a constant-time
     * comparison to avoid timing side channels.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
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

}