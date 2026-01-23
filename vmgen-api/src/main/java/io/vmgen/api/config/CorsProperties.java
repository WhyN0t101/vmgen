package io.vmgen.api.config;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vmgen.cors")
public class CorsProperties {
    /**
     * Comma-separated list of allowed origins.
     * Example: "http://localhost:5173,https://vmgen.example.com"
     */
    private String allowedOrigins = "http://localhost:3000,http://localhost:5173";

    private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";

    private String allowedHeaders = "Authorization,Content-Type";

    private boolean allowCredentials = true;

    private long maxAge = 3600L;

    /**
     * Parse comma-separated origins into a list.
     */
    public List<String> getAllowedOriginsList() {
        return Arrays.asList(allowedOrigins.split(","));
    }

    /**
     * Parse comma-separated methods into a list.
     */
    public List<String> getAllowedMethodsList() {
        return Arrays.asList(allowedMethods.split(","));
    }

    /**
     * Parse comma-separated headers into a list.
     */
    public List<String> getAllowedHeadersList() {
        return Arrays.asList(allowedHeaders.split(","));
    }
}
