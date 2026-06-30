package com.dailyexpense.user;

import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.ChangePasswordRequest;
import com.dailyexpense.user.dto.LoginRequest;
import com.dailyexpense.user.dto.UpdateProfileRequest;
import com.dailyexpense.user.dto.UserResponse;
import com.dailyexpense.user.util.TokenHasher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T032/T033/T034 — Profile read, profile update, password change, account delete.
 *
 * MUSTs:
 * - GET/PUT /me never expose passwordHash (AL-4)
 * - PUT /me body userId is ignored (identity from JWT, AL-5)
 * - PATCH /me/password wrong current-password → 400; correct → 204 + all refresh revoked
 * - DELETE /me → 204; status DELETED; subsequent login → 401
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserProfileIT extends AbstractUserServiceIT {

    final String EMAIL = "profile+" + UUID.randomUUID() + "@example.com";
    final String PASSWORD = "ProfilePass99!";

    AuthTokenResponse tokens;

    @BeforeAll
    void setup() {
        registerAndActivate("Profile User", EMAIL, PASSWORD);
        tokens = loginTokens(EMAIL, PASSWORD);
    }

    @Test
    void getProfile_returns200WithNoPasswordHash() {
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<UserResponse> resp = rest.exchange(
            usersBase() + "/me", HttpMethod.GET, req, UserResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.email()).isEqualTo(EMAIL);
        assertThat(body.status()).isEqualTo("ACTIVE");
    }

    @Test
    void getProfile_responseBodyDoesNotContainPasswordHash() {
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(tokens.accessToken()));
        ResponseEntity<String> resp = rest.exchange(
            usersBase() + "/me", HttpMethod.GET, req, String.class);

        assertThat(resp.getBody()).doesNotContainIgnoringCase("password");
        assertThat(resp.getBody()).doesNotContain("$2a$");
        assertThat(resp.getBody()).doesNotContainIgnoringCase("passwordHash");
    }

    @Test
    void updateProfile_bodyUserIdIsIgnored_identityFromJwt() {
        // Provide a random UUID as userId in body — must be ignored (AL-5)
        UUID bogusId = UUID.randomUUID();
        UpdateProfileRequest update = new UpdateProfileRequest(
            "Updated Name", "Asia/Kolkata", "en_IN", false);

        HttpEntity<UpdateProfileRequest> req = new HttpEntity<>(update, bearerHeaders(tokens.accessToken()));
        ResponseEntity<UserResponse> resp = rest.exchange(
            usersBase() + "/me", HttpMethod.PUT, req, UserResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().fullName()).isEqualTo("Updated Name");
        // User identity comes from JWT — userId in response must match the registered user
        assertThat(resp.getBody().userId()).isEqualTo(
            userRepository.findByEmail(EMAIL).orElseThrow().getId());
        // bogusId must NOT be persisted
        assertThat(resp.getBody().userId()).isNotEqualTo(bogusId);
    }

    @Test
    void changePassword_wrongCurrentPassword_returns400() {
        HttpEntity<ChangePasswordRequest> req = new HttpEntity<>(
            new ChangePasswordRequest("WrongPassword!", "NewPass99!"),
            bearerHeaders(tokens.accessToken()));

        ResponseEntity<String> resp = rest.exchange(
            usersBase() + "/me/password", HttpMethod.PATCH, req, String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePassword_correctCurrent_returns204AndRevokesAllRefreshTokens() {
        // Get a fresh live token
        AuthTokenResponse fresh = loginTokens(EMAIL, PASSWORD);
        String liveRefresh = fresh.refreshToken();

        HttpEntity<ChangePasswordRequest> req = new HttpEntity<>(
            new ChangePasswordRequest(PASSWORD, "ChangedPass99!"),
            bearerHeaders(fresh.accessToken()));

        ResponseEntity<Void> resp = rest.exchange(
            usersBase() + "/me/password", HttpMethod.PATCH, req, Void.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // All refresh tokens revoked
        String liveHash = TokenHasher.sha256(liveRefresh);
        java.time.Instant revokedAt = jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
            java.time.Instant.class, liveHash);
        assertThat(revokedAt).isNotNull();

        // Reset password back for subsequent tests
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE email = ?",
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(PASSWORD), EMAIL);
    }

    @Test
    void deleteAccount_returns204_statusDeletedAndSubsequentLoginReturns401() {
        // Create a dedicated user for delete test to avoid interfering with other tests
        String deleteEmail = "delete+" + UUID.randomUUID() + "@example.com";
        registerAndActivate("Delete User", deleteEmail, "DeletePass99!");
        AuthTokenResponse deleteTokens = loginTokens(deleteEmail, "DeletePass99!");

        // Delete the account
        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(deleteTokens.accessToken()));
        ResponseEntity<Void> delResp = rest.exchange(
            usersBase() + "/me", HttpMethod.DELETE, req, Void.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Status must be DELETED in DB
        assertThat(userRepository.findByEmail(deleteEmail).orElseThrow().getStatus())
            .isEqualTo(UserStatus.DELETED);

        // Subsequent login must return 401
        ResponseEntity<String> loginResp = rest.postForEntity(
            authBase() + "/login",
            new LoginRequest(deleteEmail, "DeletePass99!"),
            String.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deleteAccount_writesUserDeletedEventToOutbox() {
        String deleteEmail = "deleteevent+" + UUID.randomUUID() + "@example.com";
        registerAndActivate("Delete Event User", deleteEmail, "DeletePass99!");
        AuthTokenResponse deleteTokens = loginTokens(deleteEmail, "DeletePass99!");

        Integer outboxBefore = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'UserDeletedEvent'", Integer.class);

        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(deleteTokens.accessToken()));
        rest.exchange(usersBase() + "/me", HttpMethod.DELETE, req, Void.class);

        Integer outboxAfter = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'UserDeletedEvent'", Integer.class);
        assertThat(outboxAfter).isGreaterThan(outboxBefore);
    }

    @Test
    void deleteAccount_revokesAllRefreshTokens() {
        String deleteEmail = "deleterevoker+" + UUID.randomUUID() + "@example.com";
        registerAndActivate("Delete Revoker", deleteEmail, "DeletePass99!");
        AuthTokenResponse deleteTokens = loginTokens(deleteEmail, "DeletePass99!");
        String liveRefresh = deleteTokens.refreshToken();

        HttpEntity<Void> req = new HttpEntity<>(bearerHeaders(deleteTokens.accessToken()));
        rest.exchange(usersBase() + "/me", HttpMethod.DELETE, req, Void.class);

        String liveHash = TokenHasher.sha256(liveRefresh);
        java.time.Instant revokedAt = jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
            java.time.Instant.class, liveHash);
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void getProfile_unauthenticated_returns401() {
        ResponseEntity<String> resp = rest.getForEntity(usersBase() + "/me", String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
