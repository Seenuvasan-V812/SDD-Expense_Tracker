package com.dailyexpense.budget.service;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.dto.BudgetStatusResponse;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * T092 — Derives effectiveLimit, remaining, percentUsed for the active ledger.
 * effectiveLimit = budgetLimit + carriedIn
 * remaining = effectiveLimit - spent
 * percentUsed = (spent / effectiveLimit) * 100
 */
@Service
public class BudgetStatusService {

    private final BudgetRepository budgetRepository;
    private final BudgetPeriodLedgerRepository ledgerRepository;

    public BudgetStatusService(BudgetRepository budgetRepository,
                                BudgetPeriodLedgerRepository ledgerRepository) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional(readOnly = true)
    public BudgetStatusResponse getStatus(UUID budgetId, UUID userId) {
        Budget budget = budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ForbiddenOwnershipException(
                "Budget not found or access denied: " + budgetId));

        Optional<BudgetPeriodLedger> ledgerOpt = ledgerRepository.findActiveLedger(budgetId, LocalDate.now());
        if (ledgerOpt.isEmpty()) {
            return new BudgetStatusResponse(
                budgetId, null, null, null,
                budget.getBudgetLimit(), BigDecimal.ZERO,
                budget.getBudgetLimit(), BigDecimal.ZERO,
                budget.getBudgetLimit(), BigDecimal.ZERO,
                false, false
            );
        }

        BudgetPeriodLedger ledger = ledgerOpt.get();
        BigDecimal effectiveLimit = budget.getBudgetLimit().add(ledger.getCarriedIn());
        BigDecimal spent = ledger.getSpent();
        BigDecimal remaining = effectiveLimit.subtract(spent);
        BigDecimal percentUsed = effectiveLimit.compareTo(BigDecimal.ZERO) > 0
            ? spent.multiply(new BigDecimal("100")).divide(effectiveLimit, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return new BudgetStatusResponse(
            budgetId, ledger.getId(),
            ledger.getPeriodStart(), ledger.getPeriodEnd(),
            budget.getBudgetLimit(), ledger.getCarriedIn(),
            effectiveLimit, spent, remaining, percentUsed,
            ledger.isFiredEightyPercent(), ledger.isFiredExceeded()
        );
    }
}
