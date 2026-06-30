package com.dailyexpense.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 JWT issuance and verification (SEC-2 / SEC-6).
 * <ul>
 *   <li>sub = UUID (never email)</li>
 *   <li>jti = random UUID per token</li>
 *   <li>typ = "access" | "refresh"</li>
 *   <li>Secret loaded from env via calling configuration — never hardcoded (SEC-6)</li>
 * </ul>
 */
public class JwtService {

    private static final String TYP_ACCESS = "access";
    private static final String TYP_REFRESH = "refresh";
    private static final long DEFAULT_ACCESS_VALIDITY_SECS = 900L; // 15 minutes
    private static final long DEFAULT_REFRESH_VALIDITY_SECS = 7L * 24 * 60 * 60; // 7 days

    private final SecretKey signingKey;
    private final long accessValiditySecs;

    /** Production constructor — secret injected by Spring configuration from env (SEC-6). */
    public JwtService(String secret) {
        this(secret, DEFAULT_ACCESS_VALIDITY_SECS);
    }

    /** Test constructor — allows overriding token validity. */
    public JwtService(String secret, long accessValiditySecs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessValiditySecs = accessValiditySecs;
    }

    /** Issues a 15-min access token. sub=userId UUID, typ="access". */
    public String issueAccessToken(UUID userId) {
        return buildToken(userId, TYP_ACCESS, accessValiditySecs * 1000);
    }

    /** Issues a 7-day refresh token. sub=userId UUID, typ="refresh". */
    public String issueRefreshToken(UUID userId) {
        return buildToken(userId, TYP_REFRESH, DEFAULT_REFRESH_VALIDITY_SECS * 1000);
    }

    /**
     * Verifies the token is a valid, non-expired access token and returns the caller's userId.
     *
     * @throws io.jsonwebtoken.JwtException  if the signature is invalid or the token is expired
     * @throws IllegalArgumentException       if the token type is not "access"
     */
    public UUID verifyAccessToken(String token) {
        Claims claims = parseClaims(token);
        String typ = claims.get("typ", String.class);
        if (!TYP_ACCESS.equals(typ)) {
            throw new IllegalArgumentException("Expected typ=access but got typ=" + typ);
        }
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Verifies a refresh token and returns the caller's userId.
     *
     * @throws io.jsonwebtoken.JwtException  if invalid or expired
     * @throws IllegalArgumentException       if not a refresh token
     */
    public UUID verifyRefreshToken(String token) {
        Claims claims = parseClaims(token);
        String typ = claims.get("typ", String.class);
        if (!TYP_REFRESH.equals(typ)) {
            throw new IllegalArgumentException("Expected typ=refresh but got typ=" + typ);
        }
        return UUID.fromString(claims.getSubject());
    }

    /** Extracts jti claim without validating (useful for revocation lookups). */
    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    // ── private ──────────────────────────────────────────────────────────────

    private String buildToken(UUID userId, String typ, long validityMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .id(UUID.randomUUID().toString())
            .claim("typ", typ)
            .issuedAt(new Date(now))
            .expiration(new Date(now + validityMs))
            .signWith(signingKey)
            .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
