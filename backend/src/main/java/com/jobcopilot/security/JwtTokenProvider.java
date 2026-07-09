package com.jobcopilot.security;

import com.jobcopilot.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Creates and validates JWT access and refresh tokens using the jjwt 0.12.x API.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTtlMillis;
    private final long refreshTtlMillis;

    public JwtTokenProvider(AppProperties properties) {
        AppProperties.Security.Jwt jwt = properties.getSecurity().getJwt();
        byte[] keyBytes = jwt.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = jwt.getIssuer();
        this.accessTtlMillis = jwt.getAccessTokenTtlMinutes() * 60_000L;
        this.refreshTtlMillis = jwt.getRefreshTokenTtlDays() * 24L * 60L * 60_000L;
    }

    public String generateAccessToken(String username, String role) {
        return buildToken(username, role, TYPE_ACCESS, accessTtlMillis);
    }

    public String generateRefreshToken(String username, String role) {
        return buildToken(username, role, TYPE_REFRESH, refreshTtlMillis);
    }

    public long getAccessTtlSeconds() {
        return accessTtlMillis / 1000L;
    }

    private String buildToken(String username, String role, String type, long ttlMillis) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuer(issuer)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMillis)))
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class)));
    }

    /**
     * Validate signature and expiry. Returns {@code false} on any parsing/validation failure.
     */
    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parse(token).getPayload());
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
    }
}
