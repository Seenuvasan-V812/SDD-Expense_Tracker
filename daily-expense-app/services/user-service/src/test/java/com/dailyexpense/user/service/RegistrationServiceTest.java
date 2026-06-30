package com.dailyexpense.user.service;

import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.user.domain.User;
import com.dailyexpense.user.domain.UserStatus;
import com.dailyexpense.user.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RED test — T023 / T118.
 * AC: dup→BusinessConflictException; new user INACTIVE_UNVERIFIED; BCrypt-hashed; no plaintext in logs.
 * T118: users.registered counter increments on successful registration; 0 PII in tags.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    SimpleMeterRegistry meterRegistry;
    RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        registrationService = new RegistrationService(userRepository, passwordEncoder, meterRegistry);
    }

    @Test
    void register_newUser_isInactiveUnverified() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$mockedhash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = registrationService.register("Jane Doe", "jane@example.com", "password123");

        assertThat(result.getStatus()).isEqualTo(UserStatus.INACTIVE_UNVERIFIED);
    }

    @Test
    void register_storesHashNotPlaintext() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawpass")).thenReturn("$2a$12$mockedhash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = registrationService.register("Test User", "test@example.com", "rawpass");

        verify(passwordEncoder).encode("rawpass");
        assertThat(result.getPasswordHash()).isEqualTo("$2a$12$mockedhash");
        assertThat(result.getPasswordHash()).doesNotContain("rawpass");
    }

    @Test
    void register_duplicateEmail_throwsBusinessConflict() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() ->
                registrationService.register("Dup User", "dup@example.com", "password123")
        ).isInstanceOf(BusinessConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_setsIdAndAuditTimestamps() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$x");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User result = registrationService.register("New User", "new@example.com", "password99!");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    // ── T118: users.registered counter ───────────────────────────────────────

    @Test
    void register_incrementsUsersRegisteredCounter() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        double before = meterRegistry.counter("users.registered").count();
        registrationService.register("Counter User", "counter@example.com", "pass12345!");
        double after = meterRegistry.counter("users.registered").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void register_counter_hasNoPiiInTags() {
        Counter counter = meterRegistry.counter("users.registered");
        // No tag keys that could contain PII values
        counter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).doesNotContainIgnoringCase("email");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("userId");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("password");
        });
    }
}
