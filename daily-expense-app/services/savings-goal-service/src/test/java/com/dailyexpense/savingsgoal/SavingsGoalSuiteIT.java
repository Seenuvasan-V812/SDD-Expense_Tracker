package com.dailyexpense.savingsgoal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dailyexpense.savingsgoal.port.ContributionPort;

/**
 * T082 gate — smoke test: verifies application context loads with Testcontainers Postgres.
 * Full coverage is in SavingsGoalIT and ContributionReconcileIT.
 */
@SpringBootTest(
    properties = {
        "jwt.secret=test-secret-key-for-suite-it-test-at-least-32-chars",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
    }
)
@ActiveProfiles("test")
@Testcontainers
class SavingsGoalSuiteIT {

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

    @MockBean
    ContributionPort contributionPort;

    @Test
    void contextLoads() {
        // Phase 3 gate: application context starts; Flyway migrations V1–V4 apply cleanly.
    }
}
