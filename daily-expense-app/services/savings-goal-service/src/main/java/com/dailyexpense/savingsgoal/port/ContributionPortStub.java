package com.dailyexpense.savingsgoal.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T073 — Stub: returns a random expense UUID without calling expense-service.
 * Registered by ContributionPortConfig when expense-service base-url is absent.
 * In integration tests, @MockBean replaces this automatically.
 */
public class ContributionPortStub implements ContributionPort {

    @Override
    public UUID createBackingExpense(UUID userId, BigDecimal amount, LocalDate date,
                                     UUID savingsGoalId, String bearerToken) {
        return UUID.randomUUID();
    }
}
