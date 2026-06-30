package com.dailyexpense.shared.observability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: PiiMasker masks email/name; redacts tokens; has NO maskAmount method (CQ-13).
 */
class PiiMaskerTest {

    private final PiiMasker masker = new PiiMasker();

    @Test
    void masksEmail() {
        String result = masker.maskEmail("john.doe@example.com");
        assertThat(result).doesNotContain("john");
        assertThat(result).doesNotContain("doe");
        assertThat(result).doesNotContain("example");
        assertThat(result).contains("***");
    }

    @Test
    void masksName() {
        String result = masker.maskName("Ravi Kumar");
        assertThat(result).doesNotContain("Ravi");
        assertThat(result).doesNotContain("Kumar");
        assertThat(result).contains("***");
    }

    @Test
    void redactsToken() {
        String result = masker.maskToken("eyJhbGciOiJIUzI1NiJ9.abc.xyz");
        assertThat(result).isEqualTo("***REDACTED***");
    }

    @Test
    void masksEmailInLogLine() {
        String logLine = "User login attempt for user@test.com from 192.168.1.1";
        String result = masker.sanitizeLogMessage(logLine);
        assertThat(result).doesNotContain("user@test.com");
    }

    @Test
    void doesNotHaveMaskAmountMethod() {
        // CRITICAL: PiiMasker must NOT expose maskAmount — amounts are not PII in log context
        boolean hasMaskAmount = Arrays.stream(PiiMasker.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(name -> name.equals("maskAmount"));
        assertThat(hasMaskAmount)
            .as("PiiMasker MUST NOT have a maskAmount method (amounts are not PII per spec)")
            .isFalse();
    }

    @Test
    void preservesNonPiiContent() {
        String result = masker.sanitizeLogMessage("Request completed in 45ms with status 200");
        assertThat(result).contains("Request completed");
        assertThat(result).contains("45ms");
        assertThat(result).contains("status 200");
    }

    @Test
    void tokenRedactionIsConstant() {
        // All tokens → same redaction string regardless of value
        assertThat(masker.maskToken("token-a")).isEqualTo("***REDACTED***");
        assertThat(masker.maskToken("token-b")).isEqualTo("***REDACTED***");
    }
}
