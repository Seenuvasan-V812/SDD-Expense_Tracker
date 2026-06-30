package com.dailyexpense.category.outbox;

import com.dailyexpense.shared.outbox.OutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * T095 — Per-service outbox entity for category-service (AL-1: maps to category_db.outbox only).
 */
@Entity
@Table(name = "outbox")
public class CategoryOutboxEntry extends OutboxEntry {
    public CategoryOutboxEntry() { super(); }
}
