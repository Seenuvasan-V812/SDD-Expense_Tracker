package com.dailyexpense.expense.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * T063 — RecurringExpense template. Generator creates Expense occurrences from this.
 * DB-5: amount is BigDecimal. INV-9: tagIds via @ElementCollection (plain UUIDs).
 */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

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

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private RecurringFrequency frequency;

    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "max_occurrences")
    private Integer maxOccurrences;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount = 0;

    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @ElementCollection
    @CollectionTable(
        name = "recurring_expense_tags",
        joinColumns = @JoinColumn(name = "recurring_expense_id")
    )
    @Column(name = "tag_id")
    private Set<UUID> tagIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RecurringExpense() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

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

    public RecurringFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurringFrequency frequency) { this.frequency = frequency; }

    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getMaxOccurrences() { return maxOccurrences; }
    public void setMaxOccurrences(Integer maxOccurrences) { this.maxOccurrences = maxOccurrences; }

    public int getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(int generatedCount) { this.generatedCount = generatedCount; }

    public LocalDate getNextRunDate() { return nextRunDate; }
    public void setNextRunDate(LocalDate nextRunDate) { this.nextRunDate = nextRunDate; }

    public Set<UUID> getTagIds() { return tagIds; }
    public void setTagIds(Set<UUID> tagIds) { this.tagIds = tagIds != null ? tagIds : new HashSet<>(); }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
