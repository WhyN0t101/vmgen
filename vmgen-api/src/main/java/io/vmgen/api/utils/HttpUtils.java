package io.vmgen.api.utils;

import io.vmgen.api.dto.audit.AuditContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public class HttpUtils {
    private HttpUtils() {}
    
    /**
     * Extract client IP address, handling X-Forwarded-For header for proxies.
     */
    public static String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
    public static AuditContext auditContext(HttpServletRequest req, Map<String, Object> metadata) {
        String ip = getClientIp(req);
        String ua = req.getHeader("User-Agent");
        return new AuditContext(ip, ua, metadata);
    }
}
