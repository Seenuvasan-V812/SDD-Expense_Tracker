package com.dailyexpense.budget.service;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * T090 — BUD-INV-5: each threshold (EIGHTY_PERCENT, EXCEEDED) fires AT MOST ONCE per period.
 * Called within the consumer's transaction (Propagation.MANDATORY ensures same tx).
 * BUD-INV-7: deactivated budget → no alerts fired.
 * Flag set + outbox event written in the SAME tx; duplicate redelivery → flag already true → no second event.
 */
@Service
public class BudgetEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(BudgetEvaluationService.class);
    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BudgetPeriodLedgerRepository ledgerRepository;
    private final OutboxPublisher outboxPublisher;
    private final Counter budgetAlertsSentCounter;

    public BudgetEvaluationService(BudgetPeriodLedgerRepository ledgerRepository,
                                    OutboxPublisher outboxPublisher,
                                    MeterRegistry meterRegistry) {
        this.ledgerRepository = ledgerRepository;
        this.outboxPublisher = outboxPublisher;
        this.budgetAlertsSentCounter = Counter.builder("budget.alerts.sent")
            .description("Total budget alert events fired")
            .register(meterRegistry);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void evaluate(BudgetPeriodLedger ledger, Budget budget, UUID userId) {
        if (!budget.isActive()) {
            log.debug("Budget inactive — skipping evaluation: budgetId={}", budget.getId());
            return;
        }

        BigDecimal effectiveLimit = budget.getBudgetLimit().add(ledger.getCarriedIn());
        if (effectiveLimit.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal spent = ledger.getSpent();
        BigDecimal percentUsed = spent.multiply(HUNDRED).divide(effectiveLimit, 2, RoundingMode.HALF_UP);
        boolean changed = false;

        if (!ledger.isFiredEightyPercent() && percentUsed.compareTo(EIGHTY) >= 0) {
            ledger.setFiredEightyPercent(true);
            ledger.setUpdatedAt(Instant.now());
            changed = true;
            outboxPublisher.publish(EventEnvelope.builder()
                .eventType("BudgetAlertFiredEvent")
                .userId(userId)
                .producer("budget-service")
                .payload(buildAlertPayload(budget.getId(), ledger.getId(), "EIGHTY_PERCENT", percentUsed))
                .build());
            budgetAlertsSentCounter.increment();
            log.info("BudgetAlert EIGHTY_PERCENT fired: budgetId={} ledgerId={} percent={}",
                budget.getId(), ledger.getId(), percentUsed);
        }

        if (!ledger.isFiredExceeded() && spent.compareTo(effectiveLimit) > 0) {
            ledger.setFiredExceeded(true);
            ledger.setUpdatedAt(Instant.now());
            changed = true;
            outboxPublisher.publish(EventEnvelope.builder()
                .eventType("BudgetAlertFiredEvent")
                .userId(userId)
                .producer("budget-service")
                .payload(buildAlertPayload(budget.getId(), ledger.getId(), "EXCEEDED", percentUsed))
                .build());
            budgetAlertsSentCounter.increment();
            log.info("BudgetAlert EXCEEDED fired: budgetId={} ledgerId={} percent={}",
                budget.getId(), ledger.getId(), percentUsed);
        }

        if (changed) {
            ledgerRepository.save(ledger);
        }
    }

    private String buildAlertPayload(UUID budgetId, UUID ledgerId,
                                      String alertType, BigDecimal percentUsed) {
        return String.format(
            "{\"budgetId\":\"%s\",\"ledgerId\":\"%s\",\"alertType\":\"%s\",\"percentUsed\":%s}",
            budgetId, ledgerId, alertType, percentUsed);
    }
}
