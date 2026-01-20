package io.vmgen.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Argon2id defaults are reasonable; can be tuned later.
        // Spring's Argon2PasswordEncoder uses Argon2id variant.
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}


