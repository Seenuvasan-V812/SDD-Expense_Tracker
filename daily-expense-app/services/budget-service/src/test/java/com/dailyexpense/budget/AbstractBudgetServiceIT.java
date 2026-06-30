package com.dailyexpense.budget;

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
 * Shared base for budget-service integration tests.
 * Provides Testcontainers Postgres, JwtService helper, and MockMvc.
 * ActiveProfiles("test") excludes Kafka — Kafka ITs wire their own containers.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-budget-service-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractBudgetServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("budget_db")
        .withUsername("budget_user")
        .withPassword("budget_pass");

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

    protected String overallBudgetJson(String limit, String period) {
        return """
            {"scope":"OVERALL","budgetLimit":%s,"periodType":"%s","rolloverEnabled":false}
            """.formatted(limit, period);
    }

    protected String categoryBudgetJson(String categoryId, String limit, String period) {
        return """
            {"scope":"CATEGORY","categoryId":"%s","budgetLimit":%s,"periodType":"%s","rolloverEnabled":false}
            """.formatted(categoryId, limit, period);
    }
}
