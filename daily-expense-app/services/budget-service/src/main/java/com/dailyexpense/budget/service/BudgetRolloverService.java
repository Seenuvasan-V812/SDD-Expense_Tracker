package com.dailyexpense.budget.service;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * T091 — Period close/open + rollover.
 * BUD-INV-8: if rolloverEnabled, carriedIn = max(0, effectiveLimit - spent); else carriedIn = 0.
 * Idempotent: uq_budget_period_ledgers_budget_window prevents duplicate ledger open on re-run.
 * ConditionalOnProperty: disabled in tests via scheduling.enabled=false.
 */
@Service
@ConditionalOnProperty(name = "scheduling.enabled", matchIfMissing = true)
public class BudgetRolloverService {

    private static final Logger log = LoggerFactory.getLogger(BudgetRolloverService.class);

    private final BudgetRepository budgetRepository;
    private final BudgetPeriodLedgerRepository ledgerRepository;
    private final BudgetAuthoringService authoringService;

    public BudgetRolloverService(BudgetRepository budgetRepository,
                                  BudgetPeriodLedgerRepository ledgerRepository,
                                  BudgetAuthoringService authoringService) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
        this.authoringService = authoringService;
    }

    @Scheduled(fixedDelayString = "${budget.rollover.delay-ms:3600000}")
    @Transactional
    public void rolloverExpiredPeriods() {
        LocalDate today = LocalDate.now();
        List<BudgetPeriodLedger> expired = ledgerRepository.findExpiredWithoutSuccessor(today);

        for (BudgetPeriodLedger expiredLedger : expired) {
            Optional<Budget> budgetOpt = budgetRepository.findById(expiredLedger.getBudgetId());
            if (budgetOpt.isEmpty()) continue;

            Budget budget = budgetOpt.get();
            LocalDate nextPeriodStart = expiredLedger.getPeriodEnd().plusDays(1);
            BigDecimal carriedIn = computeCarriedIn(expiredLedger, budget);

            try {
                authoringService.openPeriodLedger(budget, nextPeriodStart, carriedIn, Instant.now());
                log.info("Period rolled over: budgetId={} nextPeriodStart={} carriedIn={}",
                    budget.getId(), nextPeriodStart, carriedIn);
            } catch (DataIntegrityViolationException e) {
                // uq constraint: ledger already opened (idempotent re-run), skip
                log.debug("Ledger already exists for budgetId={} periodStart={} — skipping",
                    budget.getId(), nextPeriodStart);
            }
        }
    }

    private BigDecimal computeCarriedIn(BudgetPeriodLedger ledger, Budget budget) {
        if (!budget.isRolloverEnabled()) {
            return BigDecimal.ZERO;
        }
        BigDecimal effectiveLimit = budget.getBudgetLimit().add(ledger.getCarriedIn());
        BigDecimal unspent = effectiveLimit.subtract(ledger.getSpent());
        return unspent.max(BigDecimal.ZERO);
    }
}
