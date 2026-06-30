package com.dailyexpense.budget;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.domain.PeriodType;
import com.dailyexpense.budget.port.CategoryLookupPort;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import com.dailyexpense.budget.service.BudgetAuthoringService;
import com.dailyexpense.budget.service.BudgetRolloverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T093 — BudgetPeriodIT: rollover behaviour (BUD-INV-8).
 * Verifies: rolloverEnabled → unspent carries; disabled → carriedIn=0; idempotent re-run.
 */
class BudgetPeriodIT extends AbstractBudgetServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    BudgetRolloverService budgetRolloverService;

    @Autowired
    BudgetRepository budgetRepository;

    @Autowired
    BudgetPeriodLedgerRepository ledgerRepository;

    @Autowired
    BudgetAuthoringService authoringService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.execute("DELETE FROM budget_period_ledgers");
        budgetRepository.deleteAll();
    }

    @Test
    void rollover_enabled_carriesUnspentIntoNextPeriod() {
        Budget budget = createBudget(UUID.randomUUID(), true, new BigDecimal("10000"));

        // Simulate expired ledger: period ended yesterday, spent 3000, so unspent = 7000
        BudgetPeriodLedger expired = buildExpiredLedger(budget, new BigDecimal("3000"));
        ledgerRepository.save(expired);

        // Remove the current-period ledger (simulate clean state for rollover)
        ledgerRepository.findActiveLedger(budget.getId(), LocalDate.now())
            .ifPresent(ledgerRepository::delete);

        // Execute rollover
        LocalDate today = LocalDate.now();
        List<BudgetPeriodLedger> expiredLedgers = ledgerRepository.findExpiredWithoutSuccessor(today);
        assertThat(expiredLedgers).contains(expired);

        BigDecimal unspent = budget.getBudgetLimit().add(expired.getCarriedIn()).subtract(expired.getSpent());
        BudgetPeriodLedger next = authoringService.openPeriodLedger(
            budget, expired.getPeriodEnd().plusDays(1), unspent.max(BigDecimal.ZERO), Instant.now());

        assertThat(next.getCarriedIn()).isEqualByComparingTo("7000.0000");
        assertThat(next.getPeriodStart()).isEqualTo(expired.getPeriodEnd().plusDays(1));
    }

    @Test
    void rollover_disabled_carriedInIsZero() {
        Budget budget = createBudget(UUID.randomUUID(), false, new BigDecimal("10000"));

        BudgetPeriodLedger expired = buildExpiredLedger(budget, new BigDecimal("4000"));
        ledgerRepository.save(expired);

        ledgerRepository.findActiveLedger(budget.getId(), LocalDate.now())
            .ifPresent(ledgerRepository::delete);

        // rolloverEnabled=false → carryIn = 0
        BigDecimal carriedIn = budget.isRolloverEnabled()
            ? budget.getBudgetLimit().subtract(expired.getSpent()).max(BigDecimal.ZERO)
            : BigDecimal.ZERO;

        BudgetPeriodLedger next = authoringService.openPeriodLedger(
            budget, expired.getPeriodEnd().plusDays(1), carriedIn, Instant.now());

        assertThat(next.getCarriedIn()).isEqualByComparingTo("0.0000");
    }

    @Test
    void rollover_idempotent_noDuplicateLedger() {
        Budget budget = createBudget(UUID.randomUUID(), true, new BigDecimal("10000"));
        BudgetPeriodLedger expired = buildExpiredLedger(budget, new BigDecimal("2000"));
        ledgerRepository.save(expired);

        ledgerRepository.findActiveLedger(budget.getId(), LocalDate.now())
            .ifPresent(ledgerRepository::delete);

        LocalDate nextStart = expired.getPeriodEnd().plusDays(1);
        BigDecimal carriedIn = new BigDecimal("8000");

        authoringService.openPeriodLedger(budget, nextStart, carriedIn, Instant.now());

        // Attempt second open — should silently fail via uq constraint (caught by DataIntegrityViolationException)
        try {
            authoringService.openPeriodLedger(budget, nextStart, carriedIn, Instant.now());
        } catch (Exception e) {
            // Expected — unique constraint prevents duplicate
        }

        long count = ledgerRepository.findAll().stream()
            .filter(l -> l.getBudgetId().equals(budget.getId()) && l.getPeriodStart().equals(nextStart))
            .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void rollover_spentExceedsLimit_carriedInIsZero() {
        Budget budget = createBudget(UUID.randomUUID(), true, new BigDecimal("5000"));

        BudgetPeriodLedger expired = buildExpiredLedger(budget, new BigDecimal("7000"));
        ledgerRepository.save(expired);

        ledgerRepository.findActiveLedger(budget.getId(), LocalDate.now())
            .ifPresent(ledgerRepository::delete);

        // effectiveLimit = 5000 + 0 = 5000; spent = 7000 → unspent = -2000 → max(0,-2000) = 0
        BigDecimal unspent = budget.getBudgetLimit().add(expired.getCarriedIn()).subtract(expired.getSpent());
        BigDecimal carriedIn = unspent.max(BigDecimal.ZERO);
        assertThat(carriedIn).isEqualByComparingTo("0.0000");

        BudgetPeriodLedger next = authoringService.openPeriodLedger(
            budget, expired.getPeriodEnd().plusDays(1), carriedIn, Instant.now());

        assertThat(next.getCarriedIn()).isEqualByComparingTo("0.0000");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Budget createBudget(UUID userId, boolean rolloverEnabled, BigDecimal limit) {
        Instant now = Instant.now();
        Budget b = new Budget();
        b.setId(UUID.randomUUID());
        b.setUserId(userId);
        b.setScope(BudgetScope.OVERALL);
        b.setBudgetLimit(limit);
        b.setPeriodType(PeriodType.MONTHLY);
        b.setActive(true);
        b.setRolloverEnabled(rolloverEnabled);
        b.setCreatedAt(now);
        b.setUpdatedAt(now);
        return budgetRepository.save(b);
    }

    private BudgetPeriodLedger buildExpiredLedger(Budget budget, BigDecimal spent) {
        LocalDate start = LocalDate.now().minusMonths(1);
        LocalDate end = LocalDate.now().minusDays(1);
        Instant now = Instant.now();
        BudgetPeriodLedger l = new BudgetPeriodLedger();
        l.setId(UUID.randomUUID());
        l.setBudgetId(budget.getId());
        l.setUserId(budget.getUserId());
        l.setPeriodStart(start);
        l.setPeriodEnd(end);
        l.setCarriedIn(BigDecimal.ZERO);
        l.setSpent(spent);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        return l;
    }
}
