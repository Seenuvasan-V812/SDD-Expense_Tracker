package com.dailyexpense.savingsgoal;

import com.dailyexpense.shared.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

/**
 * Shared base for savings-goal-service integration tests.
 * Provides Testcontainers Postgres, JwtService helper, and MockMvc.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-savings-goal-service-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractSavingsGoalServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("savings_goal_db")
        .withUsername("savings_goal_user")
        .withPassword("savings_goal_pass");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    protected String bearerToken(UUID userId) {
        return "Bearer " + jwtService.issueAccessToken(userId);
    }

    protected HttpHeaders authHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, bearerToken(userId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String createGoalJson(String name, String targetAmount) {
        return """
            {
              "name": "%s",
              "targetAmount": {"amount": "%s", "currency": "INR"}
            }
            """.formatted(name, targetAmount);
    }

    protected String contributionJson(String amount, String date) {
        return """
            {
              "amount": {"amount": "%s", "currency": "INR"},
              "date": "%s"
            }
            """.formatted(amount, date);
    }
}
