package com.dailyexpense.budget.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * T085 — Budget aggregate root in budget_db.
 * AL-4: never serialized as JSON — BudgetResponse DTO only.
 * DB-5: BigDecimal for all money; no double/float.
 * INV-1: userId ownership enforced at service layer (403-never-404).
 * BUD-INV-7: active=false → no threshold alerts fired (evaluated in BudgetEvaluationService).
 * BUD-INV-8: rolloverEnabled → unspent carries into next ledger period.
 */
@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private BudgetScope scope;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "budget_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal budgetLimit;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "rollover_enabled", nullable = false)
    private boolean rolloverEnabled = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Budget() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BudgetScope getScope() { return scope; }
    public void setScope(BudgetScope scope) { this.scope = scope; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public BigDecimal getBudgetLimit() { return budgetLimit; }
    public void setBudgetLimit(BigDecimal budgetLimit) { this.budgetLimit = budgetLimit; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PeriodType getPeriodType() { return periodType; }
    public void setPeriodType(PeriodType periodType) { this.periodType = periodType; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isRolloverEnabled() { return rolloverEnabled; }
    public void setRolloverEnabled(boolean rolloverEnabled) { this.rolloverEnabled = rolloverEnabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
