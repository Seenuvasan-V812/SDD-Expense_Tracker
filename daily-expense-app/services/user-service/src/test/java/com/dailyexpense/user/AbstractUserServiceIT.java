package com.dailyexpense.user;

import com.dailyexpense.user.dto.AuthTokenResponse;
import com.dailyexpense.user.dto.LoginRequest;
import com.dailyexpense.user.dto.RegisterRequest;
import com.dailyexpense.user.repository.UserRepository;
import com.dailyexpense.user.util.TokenHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
 * Shared base for user-service integration tests.
 * Each subclass gets its own Spring context (separate @SpringBootTest) but re-uses
 * the same Postgres container via the static @Container field.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "jwt.secret=test-secret-key-for-integration-tests-at-least-32-characters",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractUserServiceIT {

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
    protected int port;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String authBase() {
        return "http://localhost:" + port + "/api/v1/auth";
    }

    protected String usersBase() {
        return "http://localhost:" + port + "/api/v1/users";
    }

    /** Registers a new user and activates them by inserting a known verification token. */
    protected String registerAndActivate(String fullName, String email, String password) {
        rest.postForEntity(authBase() + "/register",
                new RegisterRequest(fullName, email, password), Void.class);

        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        String rawToken = "activation-token-" + UUID.randomUUID();
        String tokenHash = TokenHasher.sha256(rawToken);

        jdbcTemplate.update(
            "INSERT INTO email_verifications (id, user_id, token_hash, expires_at, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, tokenHash, Instant.now().plusSeconds(86400), Instant.now()
        );
        rest.getForEntity(authBase() + "/verify-email?token=" + rawToken, Void.class);
        return email;
    }

    /** Returns the raw access + refresh tokens for an already-active user. */
    protected AuthTokenResponse loginTokens(String email, String password) {
        ResponseEntity<AuthTokenResponse> resp = rest.postForEntity(
            authBase() + "/login", new LoginRequest(email, password), AuthTokenResponse.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        return resp.getBody();
    }

    /** Builds HttpHeaders with Bearer access token. */
    protected HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        return headers;
    }
}
