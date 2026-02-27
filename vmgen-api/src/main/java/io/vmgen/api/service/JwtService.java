package io.vmgen.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.vmgen.api.config.JwtProperties;
import io.vmgen.api.dto.auth.JwtClaimsDto;
import io.vmgen.api.exceptions.InvalidTokenException;
import io.vmgen.api.exceptions.SessionExpiredException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class JwtService {

    private final JwtProperties jwtProperties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;



    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().isBlank()) {
            throw new IllegalStateException("JWT secret not configured");
        }
        if (jwtProperties.getIssuer() == null || jwtProperties.getIssuer().isBlank()) {
            throw new IllegalStateException("JWT issuer not configured");
        }

        this.algorithm = Algorithm.HMAC256(jwtProperties.getSecret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(jwtProperties.getIssuer())
                .build();

        log.info("JWT service initialized with issuer: {}", jwtProperties.getIssuer());
    }


    /**
     * Generates a signed JWT token with user identity, role, and session claims.
     * Throws {@link IllegalArgumentException} if any parameter is null.
     */
    public String generateToken(UUID userId, String role, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        if (userId == null || role == null || sessionId == null || issuedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("All parameters required for token generation");
        }

        return JWT.create()
                .withIssuer(jwtProperties.getIssuer())
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .withSubject(userId.toString())
                .withClaim("role", role)
                .withClaim("sid", sessionId.toString())
                .sign(algorithm);
    }

    /**
     * Parses and validates a raw JWT token string.
     * Throws {@link SessionExpiredException} if the token is expired.
     * Throws {@link InvalidTokenException} if the token is malformed, missing claims, or fails verification.
     */
    public JwtClaimsDto parseAndValidate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            log.warn("Token validation failed: empty token");
            throw new InvalidTokenException("Token is required");
        }

        final DecodedJWT jwt;
        try {
            jwt = verifier.verify(rawToken);
        } catch (TokenExpiredException e) {
            log.debug("Token expired: {}", e.getMessage());
            throw new SessionExpiredException();
        } catch (JWTVerificationException e) {
            log.warn("Token verification failed: {}", e.getMessage());
            throw new InvalidTokenException(e);
        }

        Instant iat = jwt.getIssuedAt() != null ? jwt.getIssuedAt().toInstant() : null;
        Instant exp = jwt.getExpiresAt() != null ? jwt.getExpiresAt().toInstant() : null;

        String userIdString = jwt.getSubject();
        String role = jwt.getClaim("role").asString();
        String sidString = jwt.getClaim("sid").asString();

        if (userIdString == null || role == null || sidString == null) {
            log.warn("Token missing required claims");
            throw new InvalidTokenException("Token missing required claims");
        }

        // Validate UUIDs are well-formed
        try {
            UUID.fromString(userIdString);
            UUID.fromString(sidString);
        } catch (IllegalArgumentException e) {
            log.warn("Token contains malformed UUID: {}", e.getMessage());
            throw new InvalidTokenException("Token contains invalid user or session ID");
        }

        return new JwtClaimsDto(userIdString, role, sidString, iat, exp);
    }
}