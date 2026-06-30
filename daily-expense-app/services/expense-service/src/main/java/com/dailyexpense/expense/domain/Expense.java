package com.dailyexpense.expense.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * T053 — Expense entity for expense_db.
 * AL-4: never serialized as JSON — ExpenseResponse DTO only.
 * INV-9: tagIds via @ElementCollection (plain UUIDs); savingsGoalId plain UUID (no @ManyToOne).
 * DB-5: amount is BigDecimal — no double/float.
 */
@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "merchant", length = 255)
    private String merchant;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "savings_goal_id")
    private UUID savingsGoalId;

    @Column(name = "recurring_expense_id")
    private UUID recurringExpenseId;

    @ElementCollection
    @CollectionTable(
        name = "expense_tags",
        joinColumns = @JoinColumn(name = "expense_id")
    )
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Expense() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getSavingsGoalId() { return savingsGoalId; }
    public void setSavingsGoalId(UUID savingsGoalId) { this.savingsGoalId = savingsGoalId; }

    public UUID getRecurringExpenseId() { return recurringExpenseId; }
    public void setRecurringExpenseId(UUID recurringExpenseId) { this.recurringExpenseId = recurringExpenseId; }

    public Set<UUID> getTagIds() { return tagIds; }
    public void setTagIds(Set<UUID> tagIds) { this.tagIds = tagIds != null ? tagIds : new HashSet<>(); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
