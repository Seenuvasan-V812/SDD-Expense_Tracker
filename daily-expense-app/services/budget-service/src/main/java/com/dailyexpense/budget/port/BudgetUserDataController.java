package com.dailyexpense.budget.port;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * T117 — Internal endpoint: returns all budgets + ledger history for a userId.
 * Guarded by /internal/** permitAll in BudgetServiceSecurityConfig.
 * No expense_db / category_db / savings_goal_db SQL (AL-1).
 */
@RestController
@RequestMapping("/internal/users")
public class BudgetUserDataController {

    private final BudgetRepository budgetRepository;
    private final BudgetPeriodLedgerRepository ledgerRepository;

    public BudgetUserDataController(BudgetRepository budgetRepository,
                                    BudgetPeriodLedgerRepository ledgerRepository) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/{userId}/export-data")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> exportData(@PathVariable UUID userId) {
        List<Budget> budgets = budgetRepository
                .findByUserId(userId, PageRequest.of(0, Integer.MAX_VALUE))
                .getContent();

        List<Map<String, Object>> budgetItems = budgets.stream()
                .map(b -> {
                    List<Map<String, Object>> ledgers = ledgerRepository
                            .findAll().stream()
                            .filter(l -> l.getBudgetId().equals(b.getId()))
                            .map(l -> Map.<String, Object>of(
                                    "id", l.getId().toString(),
                                    "periodStart", l.getPeriodStart().toString(),
                                    "periodEnd", l.getPeriodEnd().toString(),
                                    "spent", l.getSpent().toPlainString(),
                                    "carriedIn", l.getCarriedIn().toPlainString()
                            ))
                            .collect(Collectors.toList());

                    return Map.<String, Object>of(
                            "id", b.getId().toString(),
                            "scope", b.getScope().name(),
                            "periodType", b.getPeriodType().name(),
                            "budgetLimit", b.getBudgetLimit().toPlainString(),
                            "active", b.isActive(),
                            "ledgers", ledgers
                    );
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("budgets", budgetItems));
    }
}
