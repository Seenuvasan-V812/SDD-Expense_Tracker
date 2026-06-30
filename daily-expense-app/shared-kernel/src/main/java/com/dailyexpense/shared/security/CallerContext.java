package com.dailyexpense.shared.security;

import java.util.UUID;

/**
 * Immutable identity context derived from a verified JWT.
 * Holds only the caller's userId (UUID from sub claim) — never email or name (CQ-13 / AL-5).
 */
public record CallerContext(UUID userId) {
}
