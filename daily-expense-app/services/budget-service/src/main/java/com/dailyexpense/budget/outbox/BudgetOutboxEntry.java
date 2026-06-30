package com.dailyexpense.budget.outbox;

import com.dailyexpense.shared.outbox.OutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * T095 — Per-service outbox entity for budget-service (AL-1: maps to budget_db.outbox only).
 */
@Entity
@Table(name = "outbox")
public class BudgetOutboxEntry extends OutboxEntry {
    public BudgetOutboxEntry() { super(); }
}
