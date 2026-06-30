package com.dailyexpense.user.repository;

import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED test — T022.
 * AC: findByEmail→Optional<User>; empty for absent.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

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

    @Autowired
    UserRepository userRepository;

    @Test
    void findByEmail_returnsUser_whenExists() {
        User user = testUser("existing@example.com");
        userRepository.save(user);

        Optional<User> result = userRepository.findByEmail("existing@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(UserStatus.INACTIVE_UNVERIFIED);
    }

    @Test
    void findByEmail_returnsEmpty_whenAbsent() {
        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_returnsCaseExact() {
        User user = testUser("Case@Example.com");
        userRepository.save(user);

        assertThat(userRepository.findByEmail("Case@Example.com")).isPresent();
        assertThat(userRepository.findByEmail("case@example.com")).isEmpty();
    }

    private User testUser(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName("Test User");
        u.setEmail(email);
        u.setPasswordHash("$2a$12$hashedpassword_placeholder");
        u.setStatus(UserStatus.INACTIVE_UNVERIFIED);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        return u;
    }
}
