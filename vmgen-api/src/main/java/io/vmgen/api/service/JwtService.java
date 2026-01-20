package io.vmgen.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.vmgen.api.config.JwtProperties;
import io.vmgen.api.dto.auth.JwtClaimsDto;
import io.vmgen.api.exceptions.InvalidTokenException;
import io.vmgen.api.exceptions.SessionExpiredException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.algorithm = Algorithm.HMAC256(jwtProperties.getSecret());
        this.verifier = JWT.require(algorithm)
                .withIssuer(jwtProperties.getIssuer())
                .build();
    }

    public String generateToken(UUID userId, String role, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        return JWT.create()
                .withIssuer(jwtProperties.getIssuer())
                .withIssuedAt(Date.from(issuedAt))
                .withExpiresAt(Date.from(expiresAt))
                .withSubject(userId.toString())
                .withClaim("role", role)
                .withClaim("sid", sessionId.toString())
                .sign(algorithm);
    }

    public JwtClaimsDto parseAndValidate(String rawToken) {
        final DecodedJWT jwt;
        try {
            jwt = verifier.verify(rawToken);
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException(e);
        }

        Instant iat = jwt.getIssuedAt() != null ? jwt.getIssuedAt().toInstant() : null;
        Instant exp = jwt.getExpiresAt() != null ? jwt.getExpiresAt().toInstant() : null;

        if (exp != null && exp.isBefore(Instant.now())) {
            throw new SessionExpiredException();
        }

        String userId = jwt.getSubject();
        String role = jwt.getClaim("role").asString();
        String sid = jwt.getClaim("sid").asString();

        if (userId == null || role == null || sid == null) {
            throw new InvalidTokenException();
        }

        return new JwtClaimsDto(userId, role, sid, iat, exp);
    }
}


