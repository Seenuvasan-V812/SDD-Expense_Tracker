package com.dailyexpense.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * RED → GREEN: JwtService — HS256, sub=UUID, expired rejected, wrong-typ rejected, secret from env (SEC-2/SEC-6).
 */
class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-for-testing-only-at-least-32-chars-long";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
    }

    @Test
    void accessTokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        UUID extracted = jwtService.verifyAccessToken(token);
        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void subClaimIsUuidNotEmail() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        UUID sub = jwtService.verifyAccessToken(token);
        // Must be a valid UUID
        assertThatCode(() -> UUID.fromString(sub.toString())).doesNotThrowAnyException();
        // Must NOT look like an email
        assertThat(sub.toString()).doesNotContain("@");
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService shortLived = new JwtService(TEST_SECRET, 1L); // 1-second expiry
        UUID userId = UUID.randomUUID();
        String token = shortLived.issueAccessToken(userId);

        Thread.sleep(1100); // wait for expiry
        assertThatThrownBy(() -> shortLived.verifyAccessToken(token))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void refreshTokenRejectedByAccessValidator() {
        UUID userId = UUID.randomUUID();
        String refreshToken = jwtService.issueRefreshToken(userId);

        // verifyAccessToken must reject tokens with typ="refresh"
        assertThatThrownBy(() -> jwtService.verifyAccessToken(refreshToken))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("typ");
    }

    @Test
    void tamperedTokenIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);
        String tampered = token.substring(0, token.length() - 3) + "XYZ";

        assertThatThrownBy(() -> jwtService.verifyAccessToken(tampered))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void wrongSecretIsRejected() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        JwtService differentSecret = new JwtService("different-secret-key-at-least-32-chars-long!!!");
        assertThatThrownBy(() -> differentSecret.verifyAccessToken(token))
            .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void accessTokenHasFifteenMinuteExpiry() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        // Parse claims without verifying (to inspect fields)
        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();

        long expiryMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(expiryMs).isBetween(899_000L, 901_000L); // ~900 seconds = 15 minutes
    }

    @Test
    void tokenContainsJtiClaim() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser()
            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();

        assertThat(claims.getId()).isNotNull();
        assertThatCode(() -> UUID.fromString(claims.getId())).doesNotThrowAnyException();
    }
}
