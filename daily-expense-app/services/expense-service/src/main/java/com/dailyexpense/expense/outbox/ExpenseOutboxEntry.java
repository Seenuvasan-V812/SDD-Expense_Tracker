package com.dailyexpense.expense.outbox;

import com.dailyexpense.shared.outbox.OutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * T057 — Per-service outbox entity for expense-service (AL-1: maps to expense_db.outbox only).
 */
@Entity
@Table(name = "outbox")
public class ExpenseOutboxEntry extends OutboxEntry {
    public ExpenseOutboxEntry() { super(); }
}
