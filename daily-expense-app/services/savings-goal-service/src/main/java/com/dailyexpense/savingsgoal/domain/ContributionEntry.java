package com.dailyexpense.savingsgoal.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T071 — ContributionEntry local entity in savings_goal_db.
 * SG-INV-4: one entry per backing Expense per goal (uq_contribution_entries_goal_expense).
 * expense_id is a cross-context UUID reference — NO @ManyToOne into expense_db (AL-1).
 * AL-4: never serialized as JSON — ContributionEntryResponse DTO only.
 */
@Entity
@Table(name = "contribution_entries")
public class ContributionEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "savings_goal_id", nullable = false, updatable = false)
    private UUID savingsGoalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ContributionSource source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ContributionEntry() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSavingsGoalId() { return savingsGoalId; }
    public void setSavingsGoalId(UUID savingsGoalId) { this.savingsGoalId = savingsGoalId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getExpenseId() { return expenseId; }
    public void setExpenseId(UUID expenseId) { this.expenseId = expenseId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public ContributionSource getSource() { return source; }
    public void setSource(ContributionSource source) { this.source = source; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
