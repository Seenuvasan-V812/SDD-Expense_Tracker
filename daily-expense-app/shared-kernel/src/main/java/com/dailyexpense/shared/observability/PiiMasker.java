package com.dailyexpense.shared.observability;

import java.util.regex.Pattern;

/**
 * Utility for sanitising log messages. Masks email addresses and personal names;
 * fully redacts tokens. Has NO maskAmount method — monetary amounts are NOT PII
 * in the logging context (CQ-13).
 */
public class PiiMasker {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    private static final String MASKED_EMAIL = "***@***.***";
    private static final String MASKED_NAME = "*** ***";
    private static final String REDACTED_TOKEN = "***REDACTED***";

    /**
     * Masks an email address. All local and domain parts are replaced.
     *
     * @param email the raw email address
     * @return a masked representation that contains no PII
     */
    public String maskEmail(String email) {
        if (email == null) return null;
        return MASKED_EMAIL;
    }

    /**
     * Masks a personal name.
     *
     * @param name the raw full name
     * @return a masked representation
     */
    public String maskName(String name) {
        if (name == null) return null;
        return MASKED_NAME;
    }

    /**
     * Fully redacts a token (JWT, API key, etc.) to a fixed constant so token
     * value cannot be reconstructed from logs.
     *
     * @param token the raw token value
     * @return {@code "***REDACTED***"}
     */
    public String maskToken(String token) {
        return REDACTED_TOKEN;
    }

    /**
     * Sanitises a free-form log message by replacing any email-shaped strings it contains.
     * Call this before passing user-controlled strings to log statements.
     *
     * @param message the raw log message
     * @return the message with any email addresses masked
     */
    public String sanitizeLogMessage(String message) {
        if (message == null) return null;
        return EMAIL_PATTERN.matcher(message).replaceAll(MASKED_EMAIL);
    }

    // NOTE: maskAmount is intentionally absent — amounts are NOT personal information.
    // Logging amounts helps with debugging and is permitted by the privacy model.
}
