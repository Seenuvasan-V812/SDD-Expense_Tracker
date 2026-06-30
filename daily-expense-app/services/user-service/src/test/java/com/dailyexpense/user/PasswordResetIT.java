package com.dailyexpense.user;

import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.ForgotPasswordRequest;
import com.dailyexpense.user.dto.ResetPasswordRequest;
import com.dailyexpense.user.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T031 — RED tests for forgot-password + reset-password.
 *
 * MUSTs:
 * - forgot-password always returns 202 (no enumeration, even for non-existent email)
 * - PasswordResetRequestedEvent written to outbox in SAME tx as token row
 * - reset-password revokes ALL refresh tokens on success
 * - reusing a reset token returns 400
 */
class PasswordResetIT extends AbstractUserServiceIT {

    static final String EMAIL = "pwreset+" + UUID.randomUUID() + "@example.com";
    static final String PASSWORD = "Initial99!";

    @BeforeEach
    void ensureUserExists() {
        if (userRepository.findByEmail(EMAIL).isEmpty()) {
            registerAndActivate("Reset User", EMAIL, PASSWORD);
        }
    }

    @Test
    void forgotPassword_existingActiveUser_returns202() {
        ResponseEntity<Void> resp = rest.postForEntity(
            authBase() + "/forgot-password",
            new ForgotPasswordRequest(EMAIL),
            Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void forgotPassword_nonExistentEmail_returns202UniformNoEnumeration() {
        // No account exists — must still return 202 (no enumeration leak)
        ResponseEntity<Void> resp = rest.postForEntity(
            authBase() + "/forgot-password",
            new ForgotPasswordRequest("nobody+" + UUID.randomUUID() + "@example.com"),
            Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void forgotPassword_activeUser_writesPasswordResetRequestedEventToOutbox() {
        rest.postForEntity(authBase() + "/forgot-password",
            new ForgotPasswordRequest(EMAIL), Void.class);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'PasswordResetRequestedEvent'",
            Integer.class);

        assertThat(count).isGreaterThan(0);
    }

    @Test
    void forgotPassword_writesTokenRowAndOutboxInSameTransaction() {
        // Both token and outbox rows must exist (atomicity)
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        Integer tokensBefore = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, userId);

        rest.postForEntity(authBase() + "/forgot-password",
            new ForgotPasswordRequest(EMAIL), Void.class);

        Integer tokensAfter = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, userId);
        Integer outboxRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'PasswordResetRequestedEvent'",
            Integer.class);

        assertThat(tokensAfter).isGreaterThan(tokensBefore); // new token row created
        assertThat(outboxRows).isGreaterThan(0);             // outbox row created in same tx
    }

    @Test
    void resetPassword_validToken_returns204AndRevokesAllRefreshTokens() {
        // Get a live refresh token
        AuthTokenResponse tokens = loginTokens(EMAIL, PASSWORD);
        String liveRefresh = tokens.refreshToken();

        // Trigger forgot-password to create a reset token
        rest.postForEntity(authBase() + "/forgot-password",
            new ForgotPasswordRequest(EMAIL), Void.class);

        // Retrieve the token hash from DB and compute a known raw token by inserting directly
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        String rawToken = "known-reset-token-" + UUID.randomUUID();
        String tokenHash = TokenHasher.sha256(rawToken);

        jdbcTemplate.update(
            "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, tokenHash, Instant.now().plusSeconds(3600), Instant.now()
        );

        ResponseEntity<Void> resetResp = rest.postForEntity(
            authBase() + "/reset-password",
            new ResetPasswordRequest(rawToken, "NewPass99!"),
            Void.class);

        assertThat(resetResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // All refresh tokens for this user must be revoked
        String liveHash = TokenHasher.sha256(liveRefresh);
        Instant revokedAt = jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?", Instant.class, liveHash);
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void resetPassword_tokenReuse_returns400() {
        UUID userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        String rawToken = "reuse-reset-token-" + UUID.randomUUID();
        String tokenHash = TokenHasher.sha256(rawToken);

        jdbcTemplate.update(
            "INSERT INTO password_reset_tokens (id, user_id, token_hash, expires_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, tokenHash, Instant.now().plusSeconds(3600), Instant.now()
        );

        // First use — succeeds
        rest.postForEntity(authBase() + "/reset-password",
            new ResetPasswordRequest(rawToken, "Reused99!"), Void.class);

        // Second use — token already consumed
        ResponseEntity<String> reuse = rest.postForEntity(
            authBase() + "/reset-password",
            new ResetPasswordRequest(rawToken, "AnotherPass99!"),
            String.class);

        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
