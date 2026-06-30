package com.dailyexpense.savingsgoal.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T071 — SavingsGoal aggregate root in savings_goal_db.
 * AL-4: never serialized as JSON — SavingsGoalResponse DTO only.
 * DB-5: BigDecimal for all money; no double/float.
 * INV-1: userId ownership enforced at service layer (403-never-404).
 * SG-INV-6: auto-complete fires once when total_contributed >= target_amount while ACTIVE.
 */
@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "total_contributed", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalContributed = BigDecimal.ZERO;

    @Column(name = "icon", length = 100)
    private String icon;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SavingsGoal() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }

    public BigDecimal getTotalContributed() { return totalContributed; }
    public void setTotalContributed(BigDecimal totalContributed) { this.totalContributed = totalContributed; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
