package io.vmgen.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class WorkerApiKeyFilter extends OncePerRequestFilter {
    
    @Value("${worker.api.key}")
    private String workerApiKey;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        
        // Only check /api/worker/** endpoints
        if (request.getRequestURI().startsWith("/api/worker")) {
            String apiKey = request.getHeader("X-Worker-Api-Key");
            
            if (apiKey == null || !apiKey.equals(workerApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid worker API key\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }
}
