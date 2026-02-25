package io.vmgen.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@Configuration
/*
 * Security-related bean configuration, including the application's
 * {@link PasswordEncoder} strategy (Argon2 with BCrypt fallback).
 */
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());

        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2", encoders);

        // Legacy BCrypt hashes in the DB have no {id} prefix, so they are
        // treated as unprefixed by DelegatingPasswordEncoder. This tells it
        // to use BCrypt as the fallback matcher for those hashes.
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());

        return delegating;
    }
}