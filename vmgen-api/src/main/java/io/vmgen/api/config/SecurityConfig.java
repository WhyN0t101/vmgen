package io.vmgen.api.config;

import io.vmgen.api.service.JwtService;
import io.vmgen.api.service.SessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOriginsList());
        configuration.setAllowedMethods(corsProperties.getAllowedMethodsList());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeadersList());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, SessionService sessionService) {
        return new JwtAuthenticationFilter(jwtService, sessionService);
    }

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            WorkerApiKeyFilter workerApiKeyFilter
    ) {
        return http
                // CSRF disabled because we use stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)

                // CORS configuration from properties
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Stateless sessions (JWT-based auth)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public authentication endpoints
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()

                        // Worker API endpoints (protected by WorkerApiKeyFilter)
                        .requestMatchers("/api/worker/**").permitAll()

                        // Admin endpoints require ADMIN role
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Template endpoints require authentication
                        .requestMatchers("/api/templates/**").authenticated()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Add filters - use UsernamePasswordAuthenticationFilter as anchor
                .addFilterBefore(workerApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .build();
    }
}