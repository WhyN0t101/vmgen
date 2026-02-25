package io.vmgen.api.config;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vmgen.cors")
@Slf4j
/*
  Binds {@code vmgen.cors.*} properties and exposes parsed CORS settings.
  Performs basic validation and logs potentially unsafe configurations at startup.
 */
public class CorsProperties {

    /**
     * Comma-separated list of allowed origins.
     * Example: "http://localhost:5173,https://vmgen.example.com"
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    private String allowedOrigins;

    private String allowedMethods = "GET,POST,PUT,PATCH,DELETE,OPTIONS";

    private String allowedHeaders = "Authorization,Content-Type";

    private boolean allowCredentials = true;

    private long maxAge = 3600L;

    /**
     * Validates the configured CORS origins and logs the effective configuration.
     * Intended to fail fast on missing configuration and highlight insecure setups.
     */
    @PostConstruct
    void validate() {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            throw new IllegalStateException(
                    "vmgen.cors.allowed-origins must be configured. " +
                            "For development, use: http://localhost:5173"
            );
        }

        List<String> origins = getAllowedOriginsList();

        // Warn about wildcard in production
        if (origins.contains("*")) {
            log.warn("CORS configured with wildcard '*' - this is insecure for production!");
        }

        // Warn about localhost in what might be production
        boolean hasLocalhost = origins.stream()
                .anyMatch(origin -> origin.contains("localhost") || origin.contains("127.0.0.1"));

        if (hasLocalhost) {
            log.info("CORS includes localhost origins - ensure this is intentional");
        }

        log.info("CORS configured with {} allowed origins", origins.size());
        log.info("CORS allowed origins: {}", origins);
    }

    /**
     * Parse comma-separated origins into a list, trimming whitespace.
     */
    public List<String> getAllowedOriginsList() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Parse comma-separated methods into a list, trimming whitespace.
     */
    public List<String> getAllowedMethodsList() {
        return Arrays.stream(allowedMethods.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Parse comma-separated headers into a list, trimming whitespace.
     */
    public List<String> getAllowedHeadersList() {
        return Arrays.stream(allowedHeaders.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}