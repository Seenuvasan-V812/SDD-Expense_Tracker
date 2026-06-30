package com.dailyexpense.user.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED test — T024.
 * AC: hash prefix $2a$12$; plaintext unrecoverable.
 */
class PasswordEncoderConfigTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfig().passwordEncoder();

    @Test
    void hashPrefixIndicatesCostOf12() {
        String hash = encoder.encode("correcthorsebatterystaple");
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void correctPasswordMatchesHash() {
        String hash = encoder.encode("MySecret99!");
        assertThat(encoder.matches("MySecret99!", hash)).isTrue();
    }

    @Test
    void wrongPasswordDoesNotMatch() {
        String hash = encoder.encode("MySecret99!");
        assertThat(encoder.matches("WrongPassword", hash)).isFalse();
    }

    @Test
    void plaintextNotContainedInHash() {
        String plain = "plaintext-should-not-appear";
        String hash = encoder.encode(plain);
        assertThat(hash).doesNotContain(plain);
    }
}
