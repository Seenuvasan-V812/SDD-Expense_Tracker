package com.dailyexpense.budget.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T085 — One active-period ledger per Budget in budget_db.
 * uq_budget_period_ledgers_budget_window: UNIQUE(budget_id, period_start) — idempotent open (BUD-INV-8).
 * fired_eighty_percent / fired_exceeded: once-per-period idempotency flags (BUD-INV-5).
 * spent is updated ONLY from expense events — never from expense_db SQL (BUD-INV-4).
 * effectiveLimit = budgetLimit + carriedIn; remaining = effectiveLimit - spent.
 */
@Entity
@Table(name = "budget_period_ledgers")
public class BudgetPeriodLedger {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "budget_id", nullable = false, updatable = false)
    private UUID budgetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "carried_in", nullable = false, precision = 19, scale = 4)
    private BigDecimal carriedIn = BigDecimal.ZERO;

    @Column(name = "spent", nullable = false, precision = 19, scale = 4)
    private BigDecimal spent = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "fired_eighty_percent", nullable = false)
    private boolean firedEightyPercent = false;

    @Column(name = "fired_exceeded", nullable = false)
    private boolean firedExceeded = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BudgetPeriodLedger() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getBudgetId() { return budgetId; }
    public void setBudgetId(UUID budgetId) { this.budgetId = budgetId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public BigDecimal getCarriedIn() { return carriedIn; }
    public void setCarriedIn(BigDecimal carriedIn) { this.carriedIn = carriedIn; }

    public BigDecimal getSpent() { return spent; }
    public void setSpent(BigDecimal spent) { this.spent = spent; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isFiredEightyPercent() { return firedEightyPercent; }
    public void setFiredEightyPercent(boolean firedEightyPercent) { this.firedEightyPercent = firedEightyPercent; }

    public boolean isFiredExceeded() { return firedExceeded; }
    public void setFiredExceeded(boolean firedExceeded) { this.firedExceeded = firedExceeded; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
