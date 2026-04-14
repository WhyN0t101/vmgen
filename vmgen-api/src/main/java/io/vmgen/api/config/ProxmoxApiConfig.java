package io.vmgen.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "proxmox.api")
@Data
@Slf4j
/*
 * Configuration for connecting to the Proxmox API.
 * Binds {@code proxmox.api.*} properties and exposes a {@link RestTemplate}
 * preconfigured with timeouts, connection pooling, and token-based authentication.
 */
public class ProxmoxApiConfig {
    
    private String url;
    private String tokenId;      // Format: user@realm!token_name
    private String tokenSecret;  // The actual token value
    private boolean verifySsl = false;
    private String node;         // Default node for operations
    
    /**
     * Validates that mandatory Proxmox API properties are set and logs
     * a warning when SSL verification is disabled.
     */
    @PostConstruct
    void validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("proxmox.api.url must be configured");
        }
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalStateException("proxmox.api.token-id must be configured");
        }
        if (tokenSecret == null || tokenSecret.isBlank()) {
            throw new IllegalStateException("proxmox.api.token-secret must be configured");
        }
        if (node == null || node.isBlank()) {
            throw new IllegalStateException("proxmox.api.node must be configured");
        }

        if (!verifySsl) {
            log.warn("Proxmox SSL verification is disabled (proxmox.api.verify-ssl=false); "
                    + "this should only be used in local or development environments.");
        }
    }

    /**
     * Builds a {@link RestTemplate} for Proxmox calls with appropriate
     * timeouts, connection pooling, and Authorization header injection.
     */
    @Bean
    public RestTemplate proxmoxRestTemplate() throws Exception {
        // Create request config with all timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(5000, TimeUnit.MILLISECONDS)  // Timeout to get connection from pool
                .setResponseTimeout(30000, TimeUnit.MILLISECONDS)           // Timeout for response
                .build();

        RestTemplate restTemplate;

        if (!verifySsl) {
            // Disable SSL verification for self-signed certs
            TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build();
            SSLConnectionSocketFactory socketFactory =
                    new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

            HttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(socketFactory)
                            .build())
                    .build();

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            restTemplate = new RestTemplate(factory);
        } else {
            HttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build();

            HttpComponentsClientHttpRequestFactory factory =
                    new HttpComponentsClientHttpRequestFactory(httpClient);

            restTemplate = new RestTemplate(factory);
        }

        // Add interceptor to inject Authorization header on every request
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "PVEAPIToken=" + tokenId + "=" + tokenSecret);
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}
