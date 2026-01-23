package io.vmgen.api.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.apache.hc.client5.http.classic.HttpClient;
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

@Configuration
@ConfigurationProperties(prefix = "proxmox.api")
@Data
public class ProxmoxApiConfig {
    
    private String url;
    private String tokenId;      // Format: user@realm!tokenname
    private String tokenSecret;  // The actual token value
    private boolean verifySsl = false;
    private String node;         // Default node for operations
    
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
    }
    
    @Bean
    public RestTemplate proxmoxRestTemplate() throws Exception {
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
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(socketFactory)
                    .build())
                .build();
            
            HttpComponentsClientHttpRequestFactory factory = 
                new HttpComponentsClientHttpRequestFactory(httpClient);
            restTemplate = new RestTemplate(factory);
        } else {
            restTemplate = new RestTemplate();
        }
        
        // Add interceptor to inject Authorization header on every request
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().add("Authorization", "PVEAPIToken=" + tokenId + "=" + tokenSecret);
            return execution.execute(request, body);
        });
        
        return restTemplate;
    }
}
