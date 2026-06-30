package com.dailyexpense.budget.service;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.domain.PeriodType;
import com.dailyexpense.budget.dto.ActivationRequest;
import com.dailyexpense.budget.dto.BudgetResponse;
import com.dailyexpense.budget.dto.CreateBudgetRequest;
import com.dailyexpense.budget.dto.RolloverToggleRequest;
import com.dailyexpense.budget.dto.UpdateBudgetRequest;
import com.dailyexpense.budget.port.CategoryLookupPort;
import com.dailyexpense.budget.repository.BudgetPeriodLedgerRepository;
import com.dailyexpense.budget.repository.BudgetRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T086/T087/T088 — Budget CRUD + activation + rollover toggle.
 * AL-2: CATEGORY scope validated via CategoryLookupPort (no direct category_db SQL).
 * BUD-INV-7: deactivated budget fires no alerts (checked in BudgetEvaluationService).
 */
@Service
public class BudgetAuthoringService {

    private static final Logger log = LoggerFactory.getLogger(BudgetAuthoringService.class);

    private final BudgetRepository budgetRepository;
    private final BudgetPeriodLedgerRepository ledgerRepository;
    private final CategoryLookupPort categoryLookupPort;

    public BudgetAuthoringService(BudgetRepository budgetRepository,
                                   BudgetPeriodLedgerRepository ledgerRepository,
                                   CategoryLookupPort categoryLookupPort) {
        this.budgetRepository = budgetRepository;
        this.ledgerRepository = ledgerRepository;
        this.categoryLookupPort = categoryLookupPort;
    }

    @Transactional
    public BudgetResponse create(UUID userId, CreateBudgetRequest req, String bearerToken) {
        BudgetScope scope = BudgetScope.valueOf(req.scope());

        if (scope == BudgetScope.CATEGORY) {
            if (req.categoryId() == null) {
                throw new BusinessConflictException("categoryId is required for CATEGORY scope budgets");
            }
            if (!categoryLookupPort.exists(req.categoryId(), bearerToken)) {
                throw new BusinessConflictException("Category not found: " + req.categoryId());
            }
        } else {
            if (req.categoryId() != null) {
                throw new BusinessConflictException("categoryId must be null for OVERALL scope budgets");
            }
        }

        if (req.budgetLimit() == null || req.budgetLimit().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessConflictException("Budget limit must be greater than 0");
        }

        Instant now = Instant.now();
        Budget budget = new Budget();
        budget.setId(UUID.randomUUID());
        budget.setUserId(userId);
        budget.setScope(scope);
        budget.setCategoryId(req.categoryId());
        budget.setBudgetLimit(req.budgetLimit());
        budget.setPeriodType(PeriodType.valueOf(req.periodType()));
        budget.setActive(true);
        budget.setRolloverEnabled(req.rolloverEnabled());
        budget.setCreatedAt(now);
        budget.setUpdatedAt(now);
        budgetRepository.save(budget);

        openPeriodLedger(budget, LocalDate.now(), java.math.BigDecimal.ZERO, now);

        log.info("Budget created: budgetId={} userId={} scope={}", budget.getId(), userId, scope);
        return BudgetResponse.from(budget);
    }

    @Transactional(readOnly = true)
    public PageResponse<BudgetResponse> list(UUID userId, Boolean active, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Budget> result = active != null
            ? budgetRepository.findByUserIdAndActive(userId, active, pr)
            : budgetRepository.findByUserId(userId, pr);
        return PageResponse.ofSpringPage(result.map(BudgetResponse::from));
    }

    @Transactional(readOnly = true)
    public BudgetResponse get(UUID budgetId, UUID userId) {
        return BudgetResponse.from(requireOwned(budgetId, userId));
    }

    @Transactional
    public BudgetResponse update(UUID budgetId, UUID userId, UpdateBudgetRequest req) {
        Budget budget = requireOwned(budgetId, userId);
        if (req.budgetLimit() != null) {
            if (req.budgetLimit().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new BusinessConflictException("Budget limit must be greater than 0");
            }
            budget.setBudgetLimit(req.budgetLimit());
        }
        budget.setUpdatedAt(Instant.now());
        budgetRepository.save(budget);
        return BudgetResponse.from(budget);
    }

    @Transactional
    public void delete(UUID budgetId, UUID userId) {
        Budget budget = requireOwned(budgetId, userId);
        budgetRepository.delete(budget);
        log.info("Budget deleted: budgetId={} userId={}", budgetId, userId);
    }

    /** T087 — BUD-INV-7: deactivated budget fires no alerts; reactivation resumes. */
    @Transactional
    public BudgetResponse setActivation(UUID budgetId, UUID userId, ActivationRequest req) {
        Budget budget = requireOwned(budgetId, userId);
        budget.setActive(req.active());
        budget.setUpdatedAt(Instant.now());
        budgetRepository.save(budget);
        log.info("Budget activation set: budgetId={} active={}", budgetId, req.active());
        return BudgetResponse.from(budget);
    }

    /** T088 — Toggle rollover; takes effect at next period close. */
    @Transactional
    public BudgetResponse setRollover(UUID budgetId, UUID userId, RolloverToggleRequest req) {
        Budget budget = requireOwned(budgetId, userId);
        budget.setRolloverEnabled(req.rolloverEnabled());
        budget.setUpdatedAt(Instant.now());
        budgetRepository.save(budget);
        log.info("Budget rollover set: budgetId={} rolloverEnabled={}", budgetId, req.rolloverEnabled());
        return BudgetResponse.from(budget);
    }

    /** Opens a new period ledger — idempotent via uq_budget_period_ledgers_budget_window. */
    public BudgetPeriodLedger openPeriodLedger(Budget budget, LocalDate periodStart,
                                                java.math.BigDecimal carriedIn, Instant now) {
        LocalDate periodEnd = computePeriodEnd(budget.getPeriodType(), periodStart);
        BudgetPeriodLedger ledger = new BudgetPeriodLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setBudgetId(budget.getId());
        ledger.setUserId(budget.getUserId());
        ledger.setPeriodStart(periodStart);
        ledger.setPeriodEnd(periodEnd);
        ledger.setCarriedIn(carriedIn);
        ledger.setCreatedAt(now);
        ledger.setUpdatedAt(now);
        return ledgerRepository.save(ledger);
    }

    private LocalDate computePeriodEnd(PeriodType type, LocalDate start) {
        return switch (type) {
            case WEEKLY  -> start.plusWeeks(1).minusDays(1);
            case MONTHLY -> start.plusMonths(1).minusDays(1);
        };
    }

    private Budget requireOwned(UUID budgetId, UUID userId) {
        return budgetRepository.findByIdAndUserId(budgetId, userId)
            .orElseThrow(() -> new ForbiddenOwnershipException(
                "Budget not found or access denied: " + budgetId));
    }
}
