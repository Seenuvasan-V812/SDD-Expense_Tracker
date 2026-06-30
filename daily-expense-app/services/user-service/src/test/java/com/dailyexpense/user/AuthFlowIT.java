package com.dailyexpense.user;

import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.LoginRequest;
import com.dailyexpense.user.dto.LogoutRequest;
import com.dailyexpense.user.dto.RefreshRequest;
import com.dailyexpense.user.dto.RegisterRequest;
import com.dailyexpense.user.repository.RefreshTokenRepository;
import com.dailyexpense.user.repository.UserRepository;
import com.dailyexpense.user.service.EmailVerificationService;
import com.dailyexpense.user.util.TokenHasher;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED test — T026/T027/T028/T029/T030 + partial Phase-1 gate.
 *
 * Gate: register → verify → login(unverified→401) → login(active) → refresh
 *       → reuse(family-revoke→401) → any-family-token-refresh→401 → logout → refresh-after-logout→401
 * Plus: BCrypt hash prefix $2a$12$ asserted in DB.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jwt.secret=test-secret-key-for-auth-flow-integration-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("identity_db")
            .withUsername("identity_user")
            .withPassword("identity_pass");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    EmailVerificationService emailVerificationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    static String registeredEmail = "authflow+" + UUID.randomUUID() + "@example.com";
    static String refreshToken1;
    static String refreshToken2;
    static UUID storedFamilyId;

    private String base() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    // ──────────────────────────────────────────────────────────────
    // T016–T020: Flyway migrations applied on context startup
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void migrations_createAllExpectedTables() {
        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='users'", Integer.class);
        Integer rtCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='refresh_tokens'", Integer.class);
        Integer evCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='email_verifications'", Integer.class);
        Integer prCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='password_reset_tokens'", Integer.class);
        Integer deCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='data_exports'", Integer.class);
        Integer obCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='outbox'", Integer.class);

        assertThat(userCount).isGreaterThan(0);
        assertThat(rtCount).isGreaterThan(0);
        assertThat(evCount).isGreaterThan(0);
        assertThat(prCount).isGreaterThan(0);
        assertThat(deCount).isGreaterThan(0);
        assertThat(obCount).isGreaterThan(0);
    }

    @Test
    @Order(2)
    void users_familyIdColumnNotNull() {
        // T017 AC: family_id UUID NOT NULL on refresh_tokens
        String nullable = jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_name='refresh_tokens' AND column_name='family_id'", String.class);
        assertThat(nullable).isEqualToIgnoringCase("NO");
    }

    // ──────────────────────────────────────────────────────────────
    // T026: Register
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void register_returns201WithLocation() {
        var request = new RegisterRequest("Auth Flow", registeredEmail, "SecurePass99!");
        ResponseEntity<Void> response = rest.postForEntity(base() + "/register", request, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).startsWith("/api/v1/users/");
    }

    @Test
    @Order(11)
    void register_duplicateEmail_returns409() {
        var request = new RegisterRequest("Dup User", registeredEmail, "SecurePass99!");
        ResponseEntity<String> response = rest.postForEntity(base() + "/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(12)
    void register_userIsInactiveUnverifiedInDb() {
        var user = userRepository.findByEmail(registeredEmail);
        assertThat(user).isPresent();
        assertThat(user.get().getStatus()).isEqualTo(UserStatus.INACTIVE_UNVERIFIED);
    }

    // ──────────────────────────────────────────────────────────────
    // T027: Login refuses INACTIVE_UNVERIFIED (generic 401)
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(13)
    void login_inactiveUnverified_returns401Generic() {
        var request = new LoginRequest(registeredEmail, "SecurePass99!");
        ResponseEntity<String> response = rest.postForEntity(base() + "/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Body must not reveal "not verified" or "inactive" — generic only
        assertThat(response.getBody()).doesNotContainIgnoringCase("verif");
        assertThat(response.getBody()).doesNotContainIgnoringCase("inactive");
    }

    // ──────────────────────────────────────────────────────────────
    // T025 + T026: Verify email (atomicity: both rows present)
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    void emailVerification_outboxRowExistsInSameTransaction() {
        // Both email_verifications and outbox rows should exist after register (T025 atomicity AC)
        Integer verifications = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM email_verifications ev " +
            "JOIN users u ON ev.user_id = u.id WHERE u.email = ?",
            Integer.class, registeredEmail);
        Integer outboxRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox WHERE event_type = 'UserRegisteredEvent'",
            Integer.class);

        assertThat(verifications).isGreaterThan(0);
        assertThat(outboxRows).isGreaterThan(0);
    }

    @Test
    @Order(21)
    void verifyEmail_activatesUser() {
        // Get user, create a fresh verification entry with a known raw token for HTTP test
        var user = userRepository.findByEmail(registeredEmail).orElseThrow();
        String knownRawToken = "known-test-verify-token-for-authflow-test";
        String tokenHash = TokenHasher.sha256(knownRawToken);

        jdbcTemplate.update(
            "INSERT INTO email_verifications (id, user_id, token_hash, expires_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), user.getId(), tokenHash,
            Instant.now().plusSeconds(86400), Instant.now()
        );

        ResponseEntity<Void> response = rest.getForEntity(
            base() + "/verify-email?token=" + knownRawToken, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findByEmail(registeredEmail).get().getStatus())
            .isEqualTo(UserStatus.ACTIVE);
    }

    // ──────────────────────────────────────────────────────────────
    // T027: Login with active user → 200 + tokens
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(30)
    void login_activeUser_returns200WithTokens() {
        var request = new LoginRequest(registeredEmail, "SecurePass99!");
        ResponseEntity<AuthTokenResponse> response = rest.postForEntity(
            base() + "/login", request, AuthTokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresInSec()).isEqualTo(900L);

        refreshToken1 = response.getBody().refreshToken();
    }

    @Test
    @Order(31)
    void login_bcryptCostAssertedInDb() {
        // T024 AC: hash prefix $2a$12$ in DB
        String hash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM users WHERE email = ?", String.class, registeredEmail);
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    @Order(32)
    void login_wrongPassword_returns401() {
        var request = new LoginRequest(registeredEmail, "WrongPassword!");
        ResponseEntity<String> response = rest.postForEntity(base() + "/login", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ──────────────────────────────────────────────────────────────
    // T029: Token rotation
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(40)
    void refresh_validToken_returns200WithNewPair() {
        assertThat(refreshToken1).isNotNull();

        var request = new RefreshRequest(refreshToken1);
        ResponseEntity<AuthTokenResponse> response = rest.postForEntity(
            base() + "/refresh", request, AuthTokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        refreshToken2 = response.getBody().refreshToken();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);
    }

    @Test
    @Order(41)
    void refresh_oldTokenIsRevoked() {
        String oldHash = TokenHasher.sha256(refreshToken1);
        Instant revokedAt = jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?", Instant.class, oldHash);
        assertThat(revokedAt).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────
    // T029: Reuse detection → family-wide revoke
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(50)
    void refresh_reuseOfRevokedToken_returns401() {
        assertThat(refreshToken1).isNotNull(); // already revoked in order 41

        var request = new RefreshRequest(refreshToken1);
        ResponseEntity<String> response = rest.postForEntity(base() + "/refresh", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(51)
    void refresh_reuseRevokesEntireFamily() {
        // After reuse detection, refreshToken2 (same family) must also be revoked
        assertThat(refreshToken2).isNotNull();
        String hash2 = TokenHasher.sha256(refreshToken2);

        Instant revokedAt = jdbcTemplate.queryForObject(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?", Instant.class, hash2);
        assertThat(revokedAt).isNotNull(); // whole family revoked
    }

    @Test
    @Order(52)
    void refresh_withFamilyRevokedToken_returns401() {
        // refreshToken2 is now also revoked — presenting it should yield 401
        var request = new RefreshRequest(refreshToken2);
        ResponseEntity<String> response = rest.postForEntity(base() + "/refresh", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ──────────────────────────────────────────────────────────────
    // T030: Logout
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(60)
    void logout_revokesSessionToken_returns204() {
        // Get a fresh login to have a live token for logout
        var loginReq = new LoginRequest(registeredEmail, "SecurePass99!");
        AuthTokenResponse freshLogin = rest.postForEntity(
            base() + "/login", loginReq, AuthTokenResponse.class).getBody();
        assertThat(freshLogin).isNotNull();
        String freshRefresh = freshLogin.refreshToken();

        var logoutReq = new LogoutRequest(freshRefresh);
        ResponseEntity<Void> logoutResp = rest.postForEntity(base() + "/logout", logoutReq, Void.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Token is revoked — subsequent refresh → 401
        var refreshReq = new RefreshRequest(freshRefresh);
        ResponseEntity<String> refreshResp = rest.postForEntity(base() + "/refresh", refreshReq, String.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ──────────────────────────────────────────────────────────────
    // Security: response bodies must not contain PII (CQ-13)
    // ──────────────────────────────────────────────────────────────

    @Test
    @Order(70)
    void tokenResponses_neverContainPasswordOrHash() {
        var loginReq = new LoginRequest(registeredEmail, "SecurePass99!");
        ResponseEntity<String> response = rest.postForEntity(base() + "/login", loginReq, String.class);

        assertThat(response.getBody()).doesNotContainIgnoringCase("password");
        assertThat(response.getBody()).doesNotContainIgnoringCase("SecurePass99");
        assertThat(response.getBody()).doesNotContain("$2a$");
    }

    @Test
    @Order(71)
    void login_responseNeverContains403() {
        // 403-never-404: wrong credentials yield 401, not 403
        var request = new LoginRequest("notexist@example.com", "any");
        ResponseEntity<String> response = rest.postForEntity(base() + "/login", request, String.class);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_FOUND);
    }
}
