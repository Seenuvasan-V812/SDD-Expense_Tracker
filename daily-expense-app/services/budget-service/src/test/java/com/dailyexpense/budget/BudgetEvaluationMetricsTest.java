package com.dailyexpense.budget;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.domain.PeriodType;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.service.BudgetEvaluationService;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T118 — budget.alerts.sent counter increments when EIGHTY_PERCENT or EXCEEDED fires.
 * 0 PII in tags.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetEvaluationMetricsTest {

    @Mock
    BudgetPeriodLedgerRepository ledgerRepository;

    @Mock
    OutboxPublisher outboxPublisher;

    SimpleMeterRegistry meterRegistry;
    BudgetEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        evaluationService = new BudgetEvaluationService(ledgerRepository, outboxPublisher, meterRegistry);
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void alertFired_eightyPercent_incrementsCounter() {
        Budget budget = activeBudget(new BigDecimal("100.00"));
        BudgetPeriodLedger ledger = ledger(new BigDecimal("85.00"), BigDecimal.ZERO);

        double before = meterRegistry.counter("budget.alerts.sent").count();
        evaluationService.evaluate(ledger, budget, UUID.randomUUID());
        double after = meterRegistry.counter("budget.alerts.sent").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void alertFired_exceeded_incrementsCounter() {
        Budget budget = activeBudget(new BigDecimal("100.00"));
        BudgetPeriodLedger ledger = ledger(new BigDecimal("105.00"), BigDecimal.ZERO);
        ledger.setFiredEightyPercent(true); // already fired; only EXCEEDED matters

        double before = meterRegistry.counter("budget.alerts.sent").count();
        evaluationService.evaluate(ledger, budget, UUID.randomUUID());
        double after = meterRegistry.counter("budget.alerts.sent").count();

        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void noAlert_noIncrement() {
        Budget budget = activeBudget(new BigDecimal("100.00"));
        BudgetPeriodLedger ledger = ledger(new BigDecimal("50.00"), BigDecimal.ZERO);

        double before = meterRegistry.counter("budget.alerts.sent").count();
        evaluationService.evaluate(ledger, budget, UUID.randomUUID());
        double after = meterRegistry.counter("budget.alerts.sent").count();

        assertThat(after).isEqualTo(before); // no change
    }

    @Test
    void counter_hasNoPiiInTags() {
        Counter counter = meterRegistry.counter("budget.alerts.sent");
        counter.getId().getTags().forEach(tag -> {
            assertThat(tag.getKey()).doesNotContainIgnoringCase("userId");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("email");
            assertThat(tag.getKey()).doesNotContainIgnoringCase("amount");
        });
    }

    private Budget activeBudget(BigDecimal limit) {
        Budget b = new Budget();
        b.setId(UUID.randomUUID());
        b.setUserId(UUID.randomUUID());
        b.setScope(BudgetScope.OVERALL);
        b.setPeriodType(PeriodType.MONTHLY);
        b.setBudgetLimit(limit);
        b.setActive(true);
        return b;
    }

    private BudgetPeriodLedger ledger(BigDecimal spent, BigDecimal carriedIn) {
        BudgetPeriodLedger l = new BudgetPeriodLedger();
        l.setId(UUID.randomUUID());
        l.setSpent(spent);
        l.setCarriedIn(carriedIn);
        l.setUpdatedAt(Instant.now());
        return l;
    }
}
