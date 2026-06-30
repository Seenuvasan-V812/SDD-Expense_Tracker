package com.dailyexpense.savingsgoal.outbox;

import com.dailyexpense.shared.outbox.OutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * T095 — Per-service outbox entity for savings-goal-service (AL-1: maps to savings_goal_db.outbox only).
 */
@Entity
@Table(name = "outbox")
public class SavingsGoalOutboxEntry extends OutboxEntry {
    public SavingsGoalOutboxEntry() { super(); }
}
